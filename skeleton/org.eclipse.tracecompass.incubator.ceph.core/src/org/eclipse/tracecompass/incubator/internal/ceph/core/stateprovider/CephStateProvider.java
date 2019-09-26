package org.eclipse.tracecompass.incubator.internal.ceph.core.stateprovider;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.CtfEnumPair;

/**
* @author hdaoud
*
*/
public class CephStateProvider extends AbstractTmfStateProvider {


    class ThreadInfo {
        public Long pid;
        public String name;
        public String type;

        public ThreadInfo(Long pid, String name, String type) {
            this.pid = pid;
            this.name = name;
            this.type = type;
        }
    }

    class SectorInfo {
        public Long sector;
        public Long ts_start;
        public Long ts_end;
        public Long nr_sector;

        public SectorInfo(Long sector, Long ts_start, Long nr_sector) {
            this.sector = sector;
            this.ts_start = ts_start;
            this.nr_sector = nr_sector;
        }
    }

    class DiskInfo {
        public HashMap<Long, SectorInfo> sectors;
        public Long dev;

        public DiskInfo(Long dev) {
            this.dev = dev;
            this.sectors = new HashMap<>();
        }
    }

    class PacketInfo {
        public Long size;
        public String daddr;

        public PacketInfo(String daddr, Long size) {
            this.daddr = daddr;
            this.size = size;
        }
    }

    class NetworkInfo {
        public ArrayList<PacketInfo> packets;
        public String name;

        public NetworkInfo(String name) {
            this.name = name;
            this.packets = new ArrayList<>();
        }
    }

    class HostInfo {
        public String name;
        public HashMap<Long, DiskInfo> disks_info;
        public HashMap<String, NetworkInfo> networks_info;

        public HostInfo(String name) {
            this.name = name;
            disks_info = new HashMap<>();
            networks_info = new HashMap<>();
        }
    }
    private final @NonNull IKernelAnalysisEventLayout fLayout;
    private static final int VERSION = 1;
    private HashMap<String, HostInfo> hosts_info = new HashMap<>();

    /**
     * @param trace trace
     * @param layout layout
     */
    @SuppressWarnings("null")
    public CephStateProvider(@NonNull ITmfTrace trace, IKernelAnalysisEventLayout layout) {
        super(trace, "dosfs"); //$NON-NLS-1$
        fLayout = layout;
    }

    @Override
    public ITmfStateProvider getNewInstance() {
        return new CephStateProvider(getTrace(), fLayout);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    protected void eventHandle(@NonNull ITmfEvent event) {
        String name = event.getName();
        String hostname = event.getTrace().getName();
        ITmfStateSystemBuilder ss = getStateSystemBuilder();
        if (ss == null) {
            return;
        }
        long ts = event.getTimestamp().toNanos();
        HostInfo hostinfo;
        if (hosts_info.containsKey(hostname)) {
            hostinfo = hosts_info.get(hostname);
        } else {
            hostinfo = new HostInfo(hostname);
            hosts_info.put(hostname, hostinfo);
        }
        /*
        if (ts == event.getTrace().getEndTime().getValue()) {
            show_network_stats(ts);
        }
        */

        switch (name) {
        case "block_rq_insert": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._tid").getValue(); //$NON-NLS-1$
            Long dev = (Long) event.getContent().getField("dev").getValue(); //$NON-NLS-1$
            Long sector = (Long) event.getContent().getField("sector").getValue(); //$NON-NLS-1$
            Long nr_sector = (Long) event.getContent().getField("nr_sector").getValue(); //$NON-NLS-1$
            DiskInfo diskinfo;
            if (hostinfo.disks_info.containsKey(dev)) {
                diskinfo = hostinfo.disks_info.get(dev);
            } else {
                diskinfo = new DiskInfo(dev);
                hostinfo.disks_info.put(dev, diskinfo);
            }

            diskinfo.sectors.put(sector, new SectorInfo(sector, ts, nr_sector));
        }
            break;
        case "block_rq_complete": { //$NON-NLS-1$
            Long dev = (Long) event.getContent().getField("dev").getValue(); //$NON-NLS-1$
            Long sector = (Long) event.getContent().getField("sector").getValue(); //$NON-NLS-1$
            if (hostinfo.disks_info.containsKey(dev)) {
                DiskInfo diskinfo = hostinfo.disks_info.get(dev);
                if (diskinfo.sectors.containsKey(sector)) {
                    SectorInfo sect = diskinfo.sectors.get(sector);
                    sect.ts_end = ts;
                    // System.out.println("sector " + sector + " is completed at " + ts);
                }
            }
        }
            break;
/*
        case "net_dev_queue": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._tid").getValue(); //$NON-NLS-1$
            String daddr = event.getContent().getField("network_header").getField("ipv4").getField("daddr").toString().replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            daddr = daddr.split("daddr=")[1].split("\\[")[1].split("\\]")[0]; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            daddr = daddr.replace(",", ".");
            String saddr = event.getContent().getField("network_header").getField("ipv4").getField("saddr").toString().replaceAll("\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            saddr = saddr.split("saddr=")[1].split("\\[")[1].split("\\]")[0]; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            saddr = saddr.replace(",", ".");
            String net_interface = (String) event.getContent().getField("name").getValue(); //$NON-NLS-1$
            net_interface = saddr;
            //Long dest_port = (Long)event.getContent().getField("network_header").getField("ipv4").getField("transport_header").getField("tcp").getField("dest_port").getValue();
            Long size = (Long) event.getContent().getField("len").getValue(); //$NON-NLS-1$

            NetworkInfo netinfo;
            if (hostinfo.networks_info.containsKey(net_interface)) {
                netinfo = hostinfo.networks_info.get(net_interface);
            } else {
                netinfo = new NetworkInfo(net_interface);
                hostinfo.networks_info.put(net_interface, netinfo);
            }

            netinfo.packets.add(new PacketInfo(daddr, size));
        }
            break;
            */
        case "osd:opwq_process_start": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._vtid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
            int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(1), statusQuark);
        }
            break;
        case "osd:opwq_process_finish": { //$NON-NLS-1$
            Long tid = (Long) event.getContent().getField("context._vtid").getValue(); //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
            int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;
        case "bfd_fsm__state_change": { //$NON-NLS-1$
            Long session_id = (Long) event.getContent().getField("sid_m").getValue(); //$NON-NLS-1$
            String nxt_state =  ((CtfEnumPair) event.getContent().getField("next_state").getValue()).getStringValue(); //$NON-NLS-1$
            int hostQuark = ss.getQuarkAbsoluteAndAdd("bfd_fsm_sid");
            int sessionQuark = ss.getQuarkRelativeAndAdd(hostQuark, String.format("%03d",session_id));
            int statusQuark = ss.getQuarkRelativeAndAdd(sessionQuark, "status"); //$NON-NLS-1$
            if (nxt_state.equalsIgnoreCase("Init")) {
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(11), statusQuark);
            }
            if (nxt_state.equalsIgnoreCase("Down")) {
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(12), statusQuark);
            }
            if (nxt_state.equalsIgnoreCase("Up")) {
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(13), statusQuark);
            }
        }
            break;

        case "hal__oam_prot_group": { //$NON-NLS-1$
            Long session_id = (Long) event.getContent().getField("prot_id").getValue(); //$NON-NLS-1$
            Long nxt_state =  (Long) event.getContent().getField("cur_stat_id").getValue();
            int hostQuark = ss.getQuarkAbsoluteAndAdd("hal__oam_prot_group");
            int sessionQuark = ss.getQuarkRelativeAndAdd(hostQuark, String.format("%03d",session_id));
            int statusQuark = ss.getQuarkRelativeAndAdd(sessionQuark, "status"); //$NON-NLS-1$
            ss.modifyAttribute(ts, TmfStateValue.newValueLong(nxt_state), statusQuark);
            }
                break;

        case "zipkin:timestamp": { //$NON-NLS-1$
            // Long tid = (Long) event.getContent().getField("context._vtid").getValue();
            // //$NON-NLS-1$
            Long pid = (Long) event.getContent().getField("context._vpid").getValue(); //$NON-NLS-1$
            String eventStr = (String) event.getContent().getField("event").getValue();
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            switch (eventStr) {
            case "async enqueueing message": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.newValueInt(2), statusQuark);
            }
                break;
            case "async writing message": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.newValueInt(3), statusQuark);
            }
                break;
            case "osd op reply": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.updateOngoingState(TmfStateValue.newValueInt(4), statusQuark);
            }
                break;
            case "finish": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            }
                break;
            case "message destructed": {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, "msg");
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, "status"); //$NON-NLS-1$
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            }
                break;

            default:
                /* Ignore other event types */
                break;
            }
        }
            break;

        default:
            /* Ignore other event types */
            break;
        }
    }


}