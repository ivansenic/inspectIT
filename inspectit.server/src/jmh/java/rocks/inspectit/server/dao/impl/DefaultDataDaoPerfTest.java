package rocks.inspectit.server.dao.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.access.BeanFactoryLocator;
import org.springframework.beans.factory.access.BeanFactoryReference;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.access.ContextSingletonBeanFactoryLocator;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import rocks.inspectit.server.CMR;
import rocks.inspectit.server.dao.DefaultDataDao;
import rocks.inspectit.shared.all.communication.data.ExceptionSensorData;
import rocks.inspectit.shared.all.communication.data.HttpTimerData;
import rocks.inspectit.shared.all.communication.data.InvocationSequenceData;
import rocks.inspectit.shared.all.communication.data.SqlStatementData;
import rocks.inspectit.shared.all.communication.data.TimerData;
import rocks.inspectit.shared.all.util.ResourcesPathResolver;

/**
 * @author Ivan Senic
 *
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DefaultDataDaoPerfTest {

	/**
	 * Number of invocations to be saved.
	 */
	private static final int INVOCATIONS = 100;

	/**
	 * Number of invocations to be added to the indexing tree.
	 */
	@Param({ "132" })
	private int children;

	/**
	 * Number of different agents to simulate.
	 */
	@Param({ "4" })
	private int agents;

	/**
	 * Number of different sensors to simulate.
	 */
	@Param({ "10" })
	private int sensors;

	/**
	 * Number of different methods to simulate.
	 */
	@Param({ "100" })
	private int methods;

	/**
	 * Spread of data in duration of 1 hour.
	 */
	@Param({ "3600000" })
	private int timestampSpread;

	@Benchmark
	@BenchmarkMode(Mode.AverageTime)
	@OperationsPerInvocation(INVOCATIONS)
	@Threads(4)
	public void saveAll(PerThreadState state) {
		List<InvocationSequenceData> invocations = new ArrayList<>();
		for (int i = 0; i < INVOCATIONS; i++) {
			InvocationSequenceData invoc = getInvocationSequenceDataInstance(children);
			invocations.add(invoc);
		}

		state.defaultDataDao.saveAll(invocations);
	}

	// @Benchmark
	@BenchmarkMode(Mode.AverageTime)
	@OperationsPerInvocation(INVOCATIONS)
	public void baseline(PerThreadState state) {
		List<InvocationSequenceData> invocations = new ArrayList<>();
		for (int i = 0; i < INVOCATIONS; i++) {
			InvocationSequenceData invoc = getInvocationSequenceDataInstance(children);
			invocations.add(invoc);
		}
	}

	@State(Scope.Benchmark)
	public static class SharedState {

		/**
		 * Class to test.
		 */
		private DefaultDataDao defaultDataDao;

		/**
		 * Factory.
		 */
		private BeanFactory beanFactory;


		@Setup(Level.Trial)
		public void setup() throws IOException {
			initLogging();

			BeanFactoryLocator beanFactoryLocator = ContextSingletonBeanFactoryLocator.getInstance();
			BeanFactoryReference beanFactoryReference = beanFactoryLocator.useBeanFactory("ctx-jmh");
			beanFactory = beanFactoryReference.getFactory();

			if (beanFactory instanceof ConfigurableApplicationContext) {
				((ConfigurableApplicationContext) beanFactory).registerShutdownHook();
			}

			defaultDataDao = beanFactory.getBean(DefaultDataDao.class);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			// ((ConfigurableBeanFactory) CMR.getBeanFactory()).destroySingletons();
		}

		private void initLogging() throws IOException {
			LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			context.reset();

			Path logPath = ResourcesPathResolver.getResourceFile(CMR.DEFAULT_LOG_FILE_NAME).toPath().toAbsolutePath();
			try (InputStream is = Files.newInputStream(logPath, StandardOpenOption.READ)) {

				configurator.doConfigure(is);
			} catch (JoranException je) { // NOPMD StatusPrinter will handle this
			}
			StatusPrinter.printInCaseOfErrorsOrWarnings(context);
		}
	}

	@State(Scope.Thread)
	public static class PerThreadState {

		/**
		 * Invocations to save.
		 */

		/**
		 * Class to test.
		 */
		private DefaultDataDao defaultDataDao;

		@Setup(Level.Trial)
		public void setup(SharedState sharedState) {
			defaultDataDao = sharedState.defaultDataDao;

		}

	}

	// private helpers
	private InvocationSequenceData getInvocationSequenceDataInstance(int childCount) {
		Random random = new Random();
		InvocationSequenceData invData = new InvocationSequenceData(new Timestamp(getRandomTimestamp(random)), getRandomPlatformIdent(random), getRandomSensorIdent(random),
				getRandomMethodIdent(random));

		setRadnomDataObject(invData, random);

		if (childCount == 0) {
			return invData;
		}

		List<InvocationSequenceData> children = new ArrayList<>();
		for (int i = 0; i < childCount;) {
			int childCountForChild = childCount / 10;
			if ((childCountForChild + i + 1) > childCount) {
				childCountForChild = childCount - i - 1;
			}
			InvocationSequenceData child = getInvocationSequenceDataInstance(childCountForChild);
			setRadnomDataObject(child, random);
			child.setParentSequence(invData);
			children.add(child);
			i += childCountForChild + 1;

		}
		invData.setChildCount(childCount);
		invData.setNestedSequences(children);
		return invData;
	}

	private long getRandomPlatformIdent(Random random) {
		return 1L + random.nextInt(agents);
	}

	private long getRandomSensorIdent(Random random) {
		return 1L + random.nextInt(sensors);
	}

	private long getRandomMethodIdent(Random random) {
		return 1L + random.nextInt(methods);
	}

	private long getRandomTimestamp(Random random) {
		return System.currentTimeMillis() - random.nextInt(timestampSpread);
	}

	private void setRadnomDataObject(InvocationSequenceData invocationSequenceData, Random random) {
		int objectSplit = random.nextInt(100);

		// http 5%, exceptions 5%, sqls 25%, timers 65%
		if (objectSplit < 5) {
			HttpTimerData httpTimerData = new HttpTimerData(new Timestamp(getRandomTimestamp(random)), getRandomPlatformIdent(random), getRandomSensorIdent(random), getRandomMethodIdent(random));
			setTime(httpTimerData);
			invocationSequenceData.setTimerData(httpTimerData);
		} else if (objectSplit < 10) {
			ExceptionSensorData exData = new ExceptionSensorData(new Timestamp(getRandomTimestamp(random)), getRandomPlatformIdent(random), getRandomSensorIdent(random), getRandomMethodIdent(random));
			invocationSequenceData.setExceptionSensorDataObjects(Collections.singletonList(exData));
		} else if (objectSplit < 35) {
			SqlStatementData sqlData = new SqlStatementData(new Timestamp(getRandomTimestamp(random)), getRandomPlatformIdent(random), getRandomSensorIdent(random), getRandomMethodIdent(random));
			setTime(sqlData);
			invocationSequenceData.setSqlStatementData(sqlData);
		} else {
			TimerData timerData = new TimerData(new Timestamp(getRandomTimestamp(random)), getRandomPlatformIdent(random), getRandomSensorIdent(random), getRandomMethodIdent(random));
			setTime(timerData);
			invocationSequenceData.setTimerData(timerData);
		}
	}

	private void setTime(TimerData timerData) {
		timerData.setCount(1L);
	}

}
