package com.github.bandwidthlimiter.bucket;

import java.util.Arrays;

public class BucketState {

    protected final long[] state;

    public BucketState(int sizeOfBandwidthsState) {
        this.state = new long[sizeOfBandwidthsState + 1];
    }

    public BucketState(BucketState previousState) {
        this.state = Arrays.copyOf(previousState.state, previousState.state.length);
    }

    public long getRefillTime() {
        return state[0];
    }

    public void setRefillTime(long refillTime) {
       state[0] = refillTime;
    }

    public BucketState(long[] snapshot) {
        this.state = snapshot;
    }

    public long[] createSnapshot() {
        return Arrays.copyOf(state, state.length);
    }

    public long getValue(int offset) {
        return state[offset + 1];
    }

    public void setValue(int offset, long value) {
        state[offset + 1] = value;
    }

    @Override
    public BucketState clone() {
        return new BucketState(this);
    }

    public void copyState(BucketState state) {
        System.arraycopy(state.state, 0, this.state, 0, this.state.length);
    }

    public void copyState(long[] state) {
        System.arraycopy(state, 0, this.state, 0, this.state.length);
    }

    public static BucketState createInitialState(BucketConfiguration configuration) {
        BucketState state = new BucketState(configuration.getStateSize());
        long currentTime = configuration.getTimeMeter().currentTime();
        for (Bandwidth bandwidth: configuration.getBandwidths()) {
            bandwidth.setupInitialState(state);
        }
        state.setRefillTime(currentTime);
        return state;
    }

    public long getAvailableTokens(Bandwidth[] bandwidths) {
        long availableByLimitation = Long.MAX_VALUE;
        long availableByGuarantee = 0;
        for (Bandwidth bandwidth : bandwidths) {
            if (bandwidth.isLimited()) {
                availableByLimitation = Math.min(availableByLimitation, bandwidth.getCurrentSize(this));
            } else {
                availableByGuarantee = bandwidth.getCurrentSize(this);
            }
        }
        return Math.max(availableByLimitation, availableByGuarantee);
    }

    public void consume(Bandwidth[] bandwidths, long toConsume) {
        for (Bandwidth bandwidth: bandwidths) {
            bandwidth.consume(this, toConsume);
        }
    }

    public long delayAfterWillBePossibleToConsume(Bandwidth[] bandwidths, long currentTime, long tokensToConsume) {
        long delayAfterWillBePossibleToConsumeLimited = 0;
        long delayAfterWillBePossibleToConsumeGuaranteed = Long.MAX_VALUE;
        for (Bandwidth bandwidth: bandwidths) {
            long delay = bandwidth.delayAfterWillBePossibleToConsume(this, currentTime, tokensToConsume);
            if (bandwidth.isGuaranteed()) {
                if (delay == 0) {
                    return 0;
                } else {
                    delayAfterWillBePossibleToConsumeGuaranteed = delay;
                }
                continue;
            }
            if (delay > delayAfterWillBePossibleToConsumeLimited) {
                delayAfterWillBePossibleToConsumeLimited = delay;
            }
        }
        return Math.min(delayAfterWillBePossibleToConsumeLimited, delayAfterWillBePossibleToConsumeGuaranteed);
    }

    public void refill(Bandwidth[] bandwidths, long currentTime) {
        for (Bandwidth bandwidth: bandwidths) {
            bandwidth.refill(this, currentTime);
        }
        setRefillTime(currentTime);
    }

}