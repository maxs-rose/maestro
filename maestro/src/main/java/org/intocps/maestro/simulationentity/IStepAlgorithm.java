package org.intocps.maestro.simulationentity;

public abstract class IStepAlgorithm {

    protected boolean maxSizeIsForced = false;
    protected Double forcedMaxSize = null;
    protected Double configuredMaxSize = null;

    public abstract String print(int indentCount);

    /**
     * This enforces the size of a maximum step irrelevant of the given step algorithm
     *
     * @param maxSize
     */
    public void forceMaxSize(double maxSize) {
        this.maxSizeIsForced = true;
        this.forcedMaxSize = maxSize;
    }

    public boolean isMaxSizeForced() {
        return this.maxSizeIsForced;
    }

    public Double getForcedMaxSize() {
        return this.forcedMaxSize;
    }

    public Double getConfiguredMaxSize() {
        return this.configuredMaxSize;
    }

    protected void setConfiguredMaxSize(double configuredMaxSize) {
        this.configuredMaxSize = configuredMaxSize;
    }

    public double getMaxSize() {
        if (isMaxSizeForced()) {
            return getForcedMaxSize();
        } else {
            return configuredMaxSize;
        }
    }

}
