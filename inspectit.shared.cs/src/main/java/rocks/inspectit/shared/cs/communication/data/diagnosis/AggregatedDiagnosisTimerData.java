package rocks.inspectit.shared.cs.communication.data.diagnosis;

import org.codehaus.jackson.annotate.JsonProperty;

import rocks.inspectit.shared.all.communication.data.InvocationSequenceData;
import rocks.inspectit.shared.all.communication.data.TimerData;
import rocks.inspectit.shared.cs.communication.data.InvocationSequenceDataHelper;

/**
 * This class represents a Aggregated Diagnosis TimerData Object with the relevant timer information
 * needed for the {@link #problemOccurrence} objects.
 *
 * @author Christian Voegele
 *
 */
public class AggregatedDiagnosisTimerData extends DiagnosisTimerData {

	/**
	 * Constructor that accepts timer data.
	 *
	 * @param timerData
	 *            the timerData used to initialize the DiagnosisTimer
	 */
	public AggregatedDiagnosisTimerData(TimerData timerData) {
		super(timerData);
		this.exclusiveCount = timerData.getExclusiveCount();
	}

	/**
	 * Constructor that accepts invocation sequence data.
	 *
	 * @param invocationSequenceData
	 *            the invocationSequenceData used to initialize the DiagnosisTimer
	 */
	public AggregatedDiagnosisTimerData(InvocationSequenceData invocationSequenceData) {
		super(invocationSequenceData);
		this.exclusiveCount = 1;
	}

	/**
	 * The exclusiveCount.
	 */
	@JsonProperty(value = "exclusiveCount")
	private double exclusiveCount;

	/**
	 * Aggregate this diagnosis timer data with the provided timerData.
	 *
	 * @param timerData
	 *            timerData of invocationSequenceData to be aggregated
	 */
	public void aggregate(TimerData timerData) {
		this.duration += timerData.getDuration();
		this.cpuDuration += timerData.getCpuDuration();
		this.exclusiveDuration += timerData.getExclusiveDuration();
		this.exclusiveCount += timerData.getExclusiveCount();
	}

	/**
	 * Aggregate this timerData with the provided timerData.
	 *
	 * @param invocationSequenceData
	 *            invocationSequenceData to be aggregated
	 */
	public void aggregate(InvocationSequenceData invocationSequenceData) {
		double dur = InvocationSequenceDataHelper.calculateDuration(invocationSequenceData);
		this.duration += dur;
		this.exclusiveDuration += dur - InvocationSequenceDataHelper.computeNestedDuration(invocationSequenceData);
		this.exclusiveCount++;
	}


	/**
	 * Gets {@link #exclusiveCount}.
	 *
	 * @return {@link #exclusiveCount}
	 */
	public final double getExclusiveCount() {
		return this.exclusiveCount;
	}

	/**
	 * Sets {@link #exclusiveCount}.
	 *
	 * @param exclusiveCount
	 *            New value for {@link #exclusiveCount}
	 */
	public final void setExclusiveCount(double exclusiveCount) {
		this.exclusiveCount = exclusiveCount;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		long temp;
		temp = Double.doubleToLongBits(this.exclusiveCount);
		result = (prime * result) + (int) (temp ^ (temp >>> 32));
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		AggregatedDiagnosisTimerData other = (AggregatedDiagnosisTimerData) obj;
		if (Double.doubleToLongBits(this.exclusiveCount) != Double.doubleToLongBits(other.exclusiveCount)) {
			return false;
		}
		return true;
	}

}
