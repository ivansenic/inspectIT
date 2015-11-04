package info.novatec.inspectit.cmr.processor.prometheus;

import info.novatec.inspectit.cmr.processor.AbstractCmrDataProcessor;
import info.novatec.inspectit.communication.DefaultData;
import info.novatec.inspectit.communication.data.HttpTimerData;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

import org.hibernate.StatelessSession;
import org.springframework.beans.factory.InitializingBean;

/**
 * Processor for prometheus HTTP data exposing.
 * 
 * @author Ivan Senic
 * 
 */
public class HttpMetricProcessor extends AbstractCmrDataProcessor implements InitializingBean {

	/**
	 * Counter for the http requests.
	 */
	private Counter httpRequestCounter;

	/**
	 * Histogram for the times.
	 */
	private Histogram httpTimeHistogram;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processData(DefaultData defaultData, StatelessSession session) {
		HttpTimerData httpData = (HttpTimerData) defaultData;

		String agent = String.valueOf(httpData.getPlatformIdent());
		String path = httpData.getUri();
		String tag = httpData.getInspectItTaggingHeaderValue();

		if (null == tag) {
			tag = "";
		}

		httpRequestCounter.labels(agent, path).inc();
		httpTimeHistogram.labels(agent, tag).observe(httpData.getDuration());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean canBeProcessed(DefaultData defaultData) {
		return defaultData instanceof HttpTimerData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		httpRequestCounter = Counter.build().name("http_request_count").help("Total number of HTTP requests").labelNames("agent", "path").register();
		httpTimeHistogram = Histogram.build().name("http_request_milliseconds").help("HTTP duration in milliseconds").labelNames("agent", "useCase").buckets(500, 1000, 3000).register();
	}

}
