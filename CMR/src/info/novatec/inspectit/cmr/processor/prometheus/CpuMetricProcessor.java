package info.novatec.inspectit.cmr.processor.prometheus;

import info.novatec.inspectit.cmr.processor.AbstractCmrDataProcessor;
import info.novatec.inspectit.communication.DefaultData;
import info.novatec.inspectit.communication.data.CpuInformationData;
import io.prometheus.client.Gauge;

import org.hibernate.StatelessSession;
import org.springframework.beans.factory.InitializingBean;

/**
 * Processor exposing the CPU metrics collected from the agents.
 * 
 * @author Ivan Senic
 * 
 */
public class CpuMetricProcessor extends AbstractCmrDataProcessor implements InitializingBean {

	/**
	 * {@link Gauge} for CPUs.
	 */
	private Gauge cpuUsage;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processData(DefaultData defaultData, StatelessSession session) {
		CpuInformationData data = (CpuInformationData) defaultData;
		cpuUsage.labels(String.valueOf(data.getPlatformIdent())).set(data.getTotalCpuUsage());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean canBeProcessed(DefaultData defaultData) {
		return defaultData instanceof CpuInformationData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		cpuUsage = Gauge.build().name("cpu_usage").help("CPU usage in %").labelNames("agent").register();
	}
}
