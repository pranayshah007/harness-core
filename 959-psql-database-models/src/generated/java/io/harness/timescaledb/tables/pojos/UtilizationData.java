/*
 * This file is generated by jOOQ.
 */
package io.harness.timescaledb.tables.pojos;


import java.io.Serializable;
import java.time.OffsetDateTime;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class UtilizationData implements Serializable {

    private static final long serialVersionUID = 1L;

    private OffsetDateTime starttime;
    private OffsetDateTime endtime;
    private String         accountid;
    private String         settingid;
    private String         instanceid;
    private String         instancetype;
    private Double         maxcpu;
    private Double         maxmemory;
    private Double         avgcpu;
    private Double         avgmemory;
    private Double         maxcpuvalue;
    private Double         maxmemoryvalue;
    private Double         avgcpuvalue;
    private Double         avgmemoryvalue;
    private String         clusterid;
    private Double         avgstoragerequestvalue;
    private Double         avgstorageusagevalue;
    private Double         avgstoragecapacityvalue;
    private Double         maxstoragerequestvalue;
    private Double         maxstorageusagevalue;

    public UtilizationData() {}

    public UtilizationData(UtilizationData value) {
        this.starttime = value.starttime;
        this.endtime = value.endtime;
        this.accountid = value.accountid;
        this.settingid = value.settingid;
        this.instanceid = value.instanceid;
        this.instancetype = value.instancetype;
        this.maxcpu = value.maxcpu;
        this.maxmemory = value.maxmemory;
        this.avgcpu = value.avgcpu;
        this.avgmemory = value.avgmemory;
        this.maxcpuvalue = value.maxcpuvalue;
        this.maxmemoryvalue = value.maxmemoryvalue;
        this.avgcpuvalue = value.avgcpuvalue;
        this.avgmemoryvalue = value.avgmemoryvalue;
        this.clusterid = value.clusterid;
        this.avgstoragerequestvalue = value.avgstoragerequestvalue;
        this.avgstorageusagevalue = value.avgstorageusagevalue;
        this.avgstoragecapacityvalue = value.avgstoragecapacityvalue;
        this.maxstoragerequestvalue = value.maxstoragerequestvalue;
        this.maxstorageusagevalue = value.maxstorageusagevalue;
    }

    public UtilizationData(
        OffsetDateTime starttime,
        OffsetDateTime endtime,
        String         accountid,
        String         settingid,
        String         instanceid,
        String         instancetype,
        Double         maxcpu,
        Double         maxmemory,
        Double         avgcpu,
        Double         avgmemory,
        Double         maxcpuvalue,
        Double         maxmemoryvalue,
        Double         avgcpuvalue,
        Double         avgmemoryvalue,
        String         clusterid,
        Double         avgstoragerequestvalue,
        Double         avgstorageusagevalue,
        Double         avgstoragecapacityvalue,
        Double         maxstoragerequestvalue,
        Double         maxstorageusagevalue
    ) {
        this.starttime = starttime;
        this.endtime = endtime;
        this.accountid = accountid;
        this.settingid = settingid;
        this.instanceid = instanceid;
        this.instancetype = instancetype;
        this.maxcpu = maxcpu;
        this.maxmemory = maxmemory;
        this.avgcpu = avgcpu;
        this.avgmemory = avgmemory;
        this.maxcpuvalue = maxcpuvalue;
        this.maxmemoryvalue = maxmemoryvalue;
        this.avgcpuvalue = avgcpuvalue;
        this.avgmemoryvalue = avgmemoryvalue;
        this.clusterid = clusterid;
        this.avgstoragerequestvalue = avgstoragerequestvalue;
        this.avgstorageusagevalue = avgstorageusagevalue;
        this.avgstoragecapacityvalue = avgstoragecapacityvalue;
        this.maxstoragerequestvalue = maxstoragerequestvalue;
        this.maxstorageusagevalue = maxstorageusagevalue;
    }

    /**
     * Getter for <code>public.utilization_data.starttime</code>.
     */
    public OffsetDateTime getStarttime() {
        return this.starttime;
    }

    /**
     * Setter for <code>public.utilization_data.starttime</code>.
     */
    public UtilizationData setStarttime(OffsetDateTime starttime) {
        this.starttime = starttime;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.endtime</code>.
     */
    public OffsetDateTime getEndtime() {
        return this.endtime;
    }

    /**
     * Setter for <code>public.utilization_data.endtime</code>.
     */
    public UtilizationData setEndtime(OffsetDateTime endtime) {
        this.endtime = endtime;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.accountid</code>.
     */
    public String getAccountid() {
        return this.accountid;
    }

    /**
     * Setter for <code>public.utilization_data.accountid</code>.
     */
    public UtilizationData setAccountid(String accountid) {
        this.accountid = accountid;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.settingid</code>.
     */
    public String getSettingid() {
        return this.settingid;
    }

    /**
     * Setter for <code>public.utilization_data.settingid</code>.
     */
    public UtilizationData setSettingid(String settingid) {
        this.settingid = settingid;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.instanceid</code>.
     */
    public String getInstanceid() {
        return this.instanceid;
    }

    /**
     * Setter for <code>public.utilization_data.instanceid</code>.
     */
    public UtilizationData setInstanceid(String instanceid) {
        this.instanceid = instanceid;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.instancetype</code>.
     */
    public String getInstancetype() {
        return this.instancetype;
    }

    /**
     * Setter for <code>public.utilization_data.instancetype</code>.
     */
    public UtilizationData setInstancetype(String instancetype) {
        this.instancetype = instancetype;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.maxcpu</code>.
     */
    public Double getMaxcpu() {
        return this.maxcpu;
    }

    /**
     * Setter for <code>public.utilization_data.maxcpu</code>.
     */
    public UtilizationData setMaxcpu(Double maxcpu) {
        this.maxcpu = maxcpu;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.maxmemory</code>.
     */
    public Double getMaxmemory() {
        return this.maxmemory;
    }

    /**
     * Setter for <code>public.utilization_data.maxmemory</code>.
     */
    public UtilizationData setMaxmemory(Double maxmemory) {
        this.maxmemory = maxmemory;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.avgcpu</code>.
     */
    public Double getAvgcpu() {
        return this.avgcpu;
    }

    /**
     * Setter for <code>public.utilization_data.avgcpu</code>.
     */
    public UtilizationData setAvgcpu(Double avgcpu) {
        this.avgcpu = avgcpu;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.avgmemory</code>.
     */
    public Double getAvgmemory() {
        return this.avgmemory;
    }

    /**
     * Setter for <code>public.utilization_data.avgmemory</code>.
     */
    public UtilizationData setAvgmemory(Double avgmemory) {
        this.avgmemory = avgmemory;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.maxcpuvalue</code>.
     */
    public Double getMaxcpuvalue() {
        return this.maxcpuvalue;
    }

    /**
     * Setter for <code>public.utilization_data.maxcpuvalue</code>.
     */
    public UtilizationData setMaxcpuvalue(Double maxcpuvalue) {
        this.maxcpuvalue = maxcpuvalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.maxmemoryvalue</code>.
     */
    public Double getMaxmemoryvalue() {
        return this.maxmemoryvalue;
    }

    /**
     * Setter for <code>public.utilization_data.maxmemoryvalue</code>.
     */
    public UtilizationData setMaxmemoryvalue(Double maxmemoryvalue) {
        this.maxmemoryvalue = maxmemoryvalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.avgcpuvalue</code>.
     */
    public Double getAvgcpuvalue() {
        return this.avgcpuvalue;
    }

    /**
     * Setter for <code>public.utilization_data.avgcpuvalue</code>.
     */
    public UtilizationData setAvgcpuvalue(Double avgcpuvalue) {
        this.avgcpuvalue = avgcpuvalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.avgmemoryvalue</code>.
     */
    public Double getAvgmemoryvalue() {
        return this.avgmemoryvalue;
    }

    /**
     * Setter for <code>public.utilization_data.avgmemoryvalue</code>.
     */
    public UtilizationData setAvgmemoryvalue(Double avgmemoryvalue) {
        this.avgmemoryvalue = avgmemoryvalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.clusterid</code>.
     */
    public String getClusterid() {
        return this.clusterid;
    }

    /**
     * Setter for <code>public.utilization_data.clusterid</code>.
     */
    public UtilizationData setClusterid(String clusterid) {
        this.clusterid = clusterid;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.avgstoragerequestvalue</code>.
     */
    public Double getAvgstoragerequestvalue() {
        return this.avgstoragerequestvalue;
    }

    /**
     * Setter for <code>public.utilization_data.avgstoragerequestvalue</code>.
     */
    public UtilizationData setAvgstoragerequestvalue(Double avgstoragerequestvalue) {
        this.avgstoragerequestvalue = avgstoragerequestvalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.avgstorageusagevalue</code>.
     */
    public Double getAvgstorageusagevalue() {
        return this.avgstorageusagevalue;
    }

    /**
     * Setter for <code>public.utilization_data.avgstorageusagevalue</code>.
     */
    public UtilizationData setAvgstorageusagevalue(Double avgstorageusagevalue) {
        this.avgstorageusagevalue = avgstorageusagevalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.avgstoragecapacityvalue</code>.
     */
    public Double getAvgstoragecapacityvalue() {
        return this.avgstoragecapacityvalue;
    }

    /**
     * Setter for <code>public.utilization_data.avgstoragecapacityvalue</code>.
     */
    public UtilizationData setAvgstoragecapacityvalue(Double avgstoragecapacityvalue) {
        this.avgstoragecapacityvalue = avgstoragecapacityvalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.maxstoragerequestvalue</code>.
     */
    public Double getMaxstoragerequestvalue() {
        return this.maxstoragerequestvalue;
    }

    /**
     * Setter for <code>public.utilization_data.maxstoragerequestvalue</code>.
     */
    public UtilizationData setMaxstoragerequestvalue(Double maxstoragerequestvalue) {
        this.maxstoragerequestvalue = maxstoragerequestvalue;
        return this;
    }

    /**
     * Getter for <code>public.utilization_data.maxstorageusagevalue</code>.
     */
    public Double getMaxstorageusagevalue() {
        return this.maxstorageusagevalue;
    }

    /**
     * Setter for <code>public.utilization_data.maxstorageusagevalue</code>.
     */
    public UtilizationData setMaxstorageusagevalue(Double maxstorageusagevalue) {
        this.maxstorageusagevalue = maxstorageusagevalue;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final UtilizationData other = (UtilizationData) obj;
        if (starttime == null) {
            if (other.starttime != null)
                return false;
        }
        else if (!starttime.equals(other.starttime))
            return false;
        if (endtime == null) {
            if (other.endtime != null)
                return false;
        }
        else if (!endtime.equals(other.endtime))
            return false;
        if (accountid == null) {
            if (other.accountid != null)
                return false;
        }
        else if (!accountid.equals(other.accountid))
            return false;
        if (settingid == null) {
            if (other.settingid != null)
                return false;
        }
        else if (!settingid.equals(other.settingid))
            return false;
        if (instanceid == null) {
            if (other.instanceid != null)
                return false;
        }
        else if (!instanceid.equals(other.instanceid))
            return false;
        if (instancetype == null) {
            if (other.instancetype != null)
                return false;
        }
        else if (!instancetype.equals(other.instancetype))
            return false;
        if (maxcpu == null) {
            if (other.maxcpu != null)
                return false;
        }
        else if (!maxcpu.equals(other.maxcpu))
            return false;
        if (maxmemory == null) {
            if (other.maxmemory != null)
                return false;
        }
        else if (!maxmemory.equals(other.maxmemory))
            return false;
        if (avgcpu == null) {
            if (other.avgcpu != null)
                return false;
        }
        else if (!avgcpu.equals(other.avgcpu))
            return false;
        if (avgmemory == null) {
            if (other.avgmemory != null)
                return false;
        }
        else if (!avgmemory.equals(other.avgmemory))
            return false;
        if (maxcpuvalue == null) {
            if (other.maxcpuvalue != null)
                return false;
        }
        else if (!maxcpuvalue.equals(other.maxcpuvalue))
            return false;
        if (maxmemoryvalue == null) {
            if (other.maxmemoryvalue != null)
                return false;
        }
        else if (!maxmemoryvalue.equals(other.maxmemoryvalue))
            return false;
        if (avgcpuvalue == null) {
            if (other.avgcpuvalue != null)
                return false;
        }
        else if (!avgcpuvalue.equals(other.avgcpuvalue))
            return false;
        if (avgmemoryvalue == null) {
            if (other.avgmemoryvalue != null)
                return false;
        }
        else if (!avgmemoryvalue.equals(other.avgmemoryvalue))
            return false;
        if (clusterid == null) {
            if (other.clusterid != null)
                return false;
        }
        else if (!clusterid.equals(other.clusterid))
            return false;
        if (avgstoragerequestvalue == null) {
            if (other.avgstoragerequestvalue != null)
                return false;
        }
        else if (!avgstoragerequestvalue.equals(other.avgstoragerequestvalue))
            return false;
        if (avgstorageusagevalue == null) {
            if (other.avgstorageusagevalue != null)
                return false;
        }
        else if (!avgstorageusagevalue.equals(other.avgstorageusagevalue))
            return false;
        if (avgstoragecapacityvalue == null) {
            if (other.avgstoragecapacityvalue != null)
                return false;
        }
        else if (!avgstoragecapacityvalue.equals(other.avgstoragecapacityvalue))
            return false;
        if (maxstoragerequestvalue == null) {
            if (other.maxstoragerequestvalue != null)
                return false;
        }
        else if (!maxstoragerequestvalue.equals(other.maxstoragerequestvalue))
            return false;
        if (maxstorageusagevalue == null) {
            if (other.maxstorageusagevalue != null)
                return false;
        }
        else if (!maxstorageusagevalue.equals(other.maxstorageusagevalue))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.starttime == null) ? 0 : this.starttime.hashCode());
        result = prime * result + ((this.endtime == null) ? 0 : this.endtime.hashCode());
        result = prime * result + ((this.accountid == null) ? 0 : this.accountid.hashCode());
        result = prime * result + ((this.settingid == null) ? 0 : this.settingid.hashCode());
        result = prime * result + ((this.instanceid == null) ? 0 : this.instanceid.hashCode());
        result = prime * result + ((this.instancetype == null) ? 0 : this.instancetype.hashCode());
        result = prime * result + ((this.maxcpu == null) ? 0 : this.maxcpu.hashCode());
        result = prime * result + ((this.maxmemory == null) ? 0 : this.maxmemory.hashCode());
        result = prime * result + ((this.avgcpu == null) ? 0 : this.avgcpu.hashCode());
        result = prime * result + ((this.avgmemory == null) ? 0 : this.avgmemory.hashCode());
        result = prime * result + ((this.maxcpuvalue == null) ? 0 : this.maxcpuvalue.hashCode());
        result = prime * result + ((this.maxmemoryvalue == null) ? 0 : this.maxmemoryvalue.hashCode());
        result = prime * result + ((this.avgcpuvalue == null) ? 0 : this.avgcpuvalue.hashCode());
        result = prime * result + ((this.avgmemoryvalue == null) ? 0 : this.avgmemoryvalue.hashCode());
        result = prime * result + ((this.clusterid == null) ? 0 : this.clusterid.hashCode());
        result = prime * result + ((this.avgstoragerequestvalue == null) ? 0 : this.avgstoragerequestvalue.hashCode());
        result = prime * result + ((this.avgstorageusagevalue == null) ? 0 : this.avgstorageusagevalue.hashCode());
        result = prime * result + ((this.avgstoragecapacityvalue == null) ? 0 : this.avgstoragecapacityvalue.hashCode());
        result = prime * result + ((this.maxstoragerequestvalue == null) ? 0 : this.maxstoragerequestvalue.hashCode());
        result = prime * result + ((this.maxstorageusagevalue == null) ? 0 : this.maxstorageusagevalue.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("UtilizationData (");

        sb.append(starttime);
        sb.append(", ").append(endtime);
        sb.append(", ").append(accountid);
        sb.append(", ").append(settingid);
        sb.append(", ").append(instanceid);
        sb.append(", ").append(instancetype);
        sb.append(", ").append(maxcpu);
        sb.append(", ").append(maxmemory);
        sb.append(", ").append(avgcpu);
        sb.append(", ").append(avgmemory);
        sb.append(", ").append(maxcpuvalue);
        sb.append(", ").append(maxmemoryvalue);
        sb.append(", ").append(avgcpuvalue);
        sb.append(", ").append(avgmemoryvalue);
        sb.append(", ").append(clusterid);
        sb.append(", ").append(avgstoragerequestvalue);
        sb.append(", ").append(avgstorageusagevalue);
        sb.append(", ").append(avgstoragecapacityvalue);
        sb.append(", ").append(maxstoragerequestvalue);
        sb.append(", ").append(maxstorageusagevalue);

        sb.append(")");
        return sb.toString();
    }
}
