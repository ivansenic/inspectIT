package rocks.inspectit.agent.java.core.impl;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import rocks.inspectit.agent.java.IThreadTransformHelper;
import rocks.inspectit.agent.java.core.ICoreService;
import rocks.inspectit.agent.java.core.IPlatformManager;
import rocks.inspectit.agent.java.sensor.jmx.IJmxSensor;
import rocks.inspectit.agent.java.sensor.platform.IPlatformSensor;
import rocks.inspectit.agent.java.util.AgentAwareThread;
import rocks.inspectit.shared.all.communication.DefaultData;
import rocks.inspectit.shared.all.communication.SystemSensorData;
import rocks.inspectit.shared.all.communication.data.eum.AbstractEUMData;
import rocks.inspectit.shared.all.spring.logger.Log;
import rocks.inspectit.shared.all.util.ExecutorServiceUtils;

/**
 * Default implementation of the {@link ICoreService} interface.
 *
 * @author Patrice Bouillet
 * @author Eduard Tudenhoefner
 * @author Alfred Krauss
 * @author Matthias Huber
 * @author Ivan Senic
 *
 */
// must depend on platform manager so that configuration is received
@Component
@DependsOn({ "platformManager" })
public class CoreService implements ICoreService {

	/**
	 * The default refresh time.
	 */
	private static final long DEFAULT_REFRESH_TIME = 1000L;

	/**
	 * The logger of the class.
	 */
	@Log
	Logger log;

	/**
	 * Platform manager.
	 */
	@Autowired
	private IPlatformManager platformManager;

	/**
	 * All platform sensors.
	 */
	@Autowired(required = false)
	private List<IPlatformSensor> platformSensors;

	/**
	 * All jmx sensors.
	 */
	@Autowired(required = false)
	private List<IJmxSensor> jmxSensors;

	/**
	 * Executor service that other components can use for asynchronous tasks.
	 */
	@Autowired
	@Qualifier("coreServiceExecutorService")
	private ScheduledExecutorService executorService;

	/**
	 * {@link IThreadTransformHelper} to use to disable transformations done in the threads started
	 * by core service.
	 */
	@Autowired
	IThreadTransformHelper threadTransformHelper;

	/**
	 * Default data handler for the disruptor.
	 */
	@Autowired
	private DefaultDataHandler defaultDataHandler;

	// TODO: confluence:
	// https://confluence.novatec-gmbh.de/display/~mwa/Aggregation+of+DefaultData%28s%29+on+the+agent
	/**
	 * Instance to the {@link Disruptor} where data for sending will be stored.
	 */
	private Disruptor<DefaultDataWrapper> disruptor;

	/**
	 * Disruptor RingBuffer.
	 */
	private RingBuffer<DefaultDataWrapper> ringBuffer;

	/**
	 * The refresh time for the platformSensorRefresher thread in ms.
	 */
	private long sensorRefreshTime = DEFAULT_REFRESH_TIME;

	/**
	 * If core service is in the phase of shutdown.
	 */
	private volatile boolean shutdown = false;

	/**
	 * Counter which is used by collision of data adding.
	 */
	private AtomicLong counter = new AtomicLong(0);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addDefaultData(DefaultData defaultData) {
		// ignore any data adding if we are in the shutdown phase
		if (shutdown) {
			return;
		}

		try {
			// grab the next sequence, never wait for it to be available so that that we don't block
			long sequence = ringBuffer.tryNext();
			try {
				// Get the entry in the disruptor for the sequence and simply change the reference
				DefaultDataWrapper defaultDataWrapper = ringBuffer.get(sequence);
				defaultDataWrapper.setDefaultData(defaultData);
			} finally {
				ringBuffer.publish(sequence);
			}
		} catch (InsufficientCapacityException e) {
			// TODO Meaningful logging
			log.error("Disruptor Capacity reached. Dropping data...");
			System.out.println("Disruptor Capacity reached. Dropping data...");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addEUMData(AbstractEUMData eumData) {
		addDefaultData(eumData);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@PostConstruct
	public void start() {
		// start disruptor
		startDisruptor();

		// schedule the sensor refresher runnable
		executorService.schedule(new SensorRefresher(), sensorRefreshTime, TimeUnit.MILLISECONDS);

		// add shutdown hook
		Runtime.getRuntime().addShutdownHook(new ShutdownHookSender());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@PreDestroy
	public void stop() {
		if (shutdown) {
			return;
		}

		// mark shutdown started
		shutdown = true;

		// shutdown disruptor
		stopDisruptor();

		// kill executor service
		shutDownExecutorService(executorService);
	}

	/**
	 * Starts the disruptor.
	 */
	@SuppressWarnings("unchecked")
	private void startDisruptor() {
		// Specify the size of the ring buffer, must be power of 2.
		// TODO read from configuration
		int bufferSize = 1024;

		// TODO factory
		disruptor = new Disruptor<DefaultDataWrapper>(new DefaultDataFactory(), bufferSize, Executors.defaultThreadFactory(), ProducerType.MULTI, new BlockingWaitStrategy());

		// Connect the handler
		disruptor.handleEventsWith(defaultDataHandler);

		// Start the Disruptor, starts all threads running
		disruptor.start();

		// Get the ring buffer from the Disruptor to be used for publishing.
		ringBuffer = disruptor.getRingBuffer();
	}

	/**
	 * Stops the disruptor.
	 */
	private void stopDisruptor() {
		disruptor.shutdown();
	}

	/**
	 * Correctly shuts down the executor service.
	 *
	 * @param executorService
	 *            Executor service to shut down.
	 */
	private void shutDownExecutorService(ExecutorService executorService) {
		ExecutorServiceUtils.shutdownExecutor(executorService, 5, TimeUnit.SECONDS);
	}

	/**
	 * Returns the current refresh time of the platform sensors.
	 *
	 * @return The platform sensor refresh time.
	 */
	public long getSensorRefreshTime() {
		return sensorRefreshTime;
	}

	/**
	 * Sets the platform sensor refresh time.
	 *
	 * @param sensorRefreshTime
	 *            The platform sensor refresh time to set.
	 */
	public void setSensorRefreshTime(long sensorRefreshTime) {
		this.sensorRefreshTime = sensorRefreshTime;
	}

	/**
	 * The SensorRefresher is a {@link Runnable} running in sensorRefreshTime intervals and updates
	 * the information of the platform and jmx sensor.
	 *
	 * @author Eduard Tudenhoefner
	 * @author Alfred Krauss
	 * @author Ivan Senic
	 *
	 */
	private class SensorRefresher implements Runnable {

		/**
		 * Counts the number of iterations from the start. Used to distinguish between reset, gather
		 * and get phase.
		 */
		private long count = 0;

		/**
		 * Defines how many iterations are gathered (and aggregated within the specific sensors)
		 * before the data is retrieved from the sensors.
		 */
		private static final int DATA_COLLECT_ITERATION = 5;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run() {
			try {
				// iterate the platformSensors and update the information
				if (CollectionUtils.isNotEmpty(platformSensors)) {
					count++;

					if (count == 1) {
						for (IPlatformSensor platformSensor : platformSensors) {
							platformSensor.reset();
						}
					}

					Iterator<IPlatformSensor> platformSensorIterator = platformSensors.iterator();
					while (platformSensorIterator.hasNext()) {
						IPlatformSensor platformSensor = platformSensorIterator.next();
						try {
							platformSensor.gather();
						} catch (Exception e) {
							// Critical error happend! Logging state and removing the sensor to
							// avoid further failing iterations.
							log.error("Platform sensor " + platformSensor.getClass().getSimpleName() + " cannot update data! Platform sensor shuts down. No metrics will be provided.", e);

							// Removing sensor from the sensor list to not gather data anymore.
							platformSensorIterator.remove();
						}
					}

					if (count == DATA_COLLECT_ITERATION) {
						for (IPlatformSensor platformSensor : platformSensors) {
							SystemSensorData systemSensorData = platformSensor.get();

							if (null != systemSensorData) {
								CoreService.this.addDefaultData(systemSensorData);
							}
						}

						count = 0;
					}
				}

				// iterate the jmxSensors and update the information
				if (CollectionUtils.isNotEmpty(jmxSensors)) {
					for (IJmxSensor jmxSensor : jmxSensors) {
						jmxSensor.update(CoreService.this);
					}
				}
			} finally {
				// reschedule the runnable
				executorService.schedule(this, sensorRefreshTime, TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * Used for the JVM Shutdown. Ensure that all threads are closed correctly and tries to send
	 * data one last time to prevent data loss.
	 *
	 * @author Stefan Siegl
	 */
	private class ShutdownHookSender extends AgentAwareThread {

		/**
		 * Default constructor.
		 */
		ShutdownHookSender() {
			super(threadTransformHelper);
		}

		@Override
		public void run() {
			// call super to perform needed pre-run operations
			super.run();
			// Stop the CoreService services, this includes shutdown of the disruptor and sending of
			// remaining data

			log.info("Shutdown initialized, sending remaining data");
			CoreService.this.stop();

			// unregister
			log.info("Unregistering the Agent");
			platformManager.unregisterPlatform();
		}
	}

}