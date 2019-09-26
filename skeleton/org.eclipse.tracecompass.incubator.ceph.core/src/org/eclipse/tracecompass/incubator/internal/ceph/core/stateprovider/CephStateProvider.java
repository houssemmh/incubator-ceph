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

    private static final String CONTEXT_VPID = "context._vpid"; //$NON-NLS-1$
    private static final String CONTEXT_VTID = "context._vtid"; //$NON-NLS-1$
    private static final String QUARK_STATUS = "status"; //$NON-NLS-1$
    private static final String QUARK_MSG = "msg"; //$NON-NLS-1$
    private static final String FIELD_EVENT = "event"; //$NON-NLS-1$
    private static final String EVENT_PROCESS_START = "osd:opwq_process_start"; //$NON-NLS-1$
    private static final String EVENT_PROCESS_FINISH = "osd:opwq_process_finish"; //$NON-NLS-1$
    private static final String EVENT_ZIPKIN = "zipkin:timestamp"; //$NON-NLS-1$
    private static final String ZIPKIN_ENQUEUE = "async enqueueing message"; //$NON-NLS-1$
    private static final String ZIPKIN_WRITE = "async writing message"; //$NON-NLS-1$
    private static final String ZIPKIN_OSD_REPLY = "osd op reply"; //$NON-NLS-1$
    private static final String ZIPKIN_FINISH = "finish"; //$NON-NLS-1$
    private static final String ZIPKIN_MSG_DESTRUCTED = "message destructed"; //$NON-NLS-1$

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

        switch (name) {
        case EVENT_PROCESS_START: {
            Long tid = (Long) event.getContent().getField(CONTEXT_VTID).getValue();
            Long pid = (Long) event.getContent().getField(CONTEXT_VPID).getValue();
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
            int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, QUARK_STATUS);
            ss.modifyAttribute(ts, TmfStateValue.newValueInt(1), statusQuark);
        }
            break;
        case EVENT_PROCESS_FINISH: {
            Long tid = (Long) event.getContent().getField(CONTEXT_VTID).getValue();
            Long pid = (Long) event.getContent().getField(CONTEXT_VPID).getValue();
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
            int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, tid.toString());
            int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, QUARK_STATUS);
            ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
        }
            break;

        case EVENT_ZIPKIN: {
            Long pid = (Long) event.getContent().getField(CONTEXT_VPID).getValue();
            String eventStr = (String) event.getContent().getField(FIELD_EVENT).getValue();
            int hostQuark = ss.getQuarkAbsoluteAndAdd(hostname);
            switch (eventStr) {
            case ZIPKIN_ENQUEUE: {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, QUARK_MSG);
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, QUARK_STATUS);
                ss.modifyAttribute(ts, TmfStateValue.newValueInt(2), statusQuark);
            }
                break;
            case ZIPKIN_WRITE: {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, QUARK_MSG);
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, QUARK_STATUS);
                ss.modifyAttribute(ts, TmfStateValue.newValueInt(3), statusQuark);
            }
                break;
            case ZIPKIN_OSD_REPLY: {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, QUARK_MSG);
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, QUARK_STATUS);
                ss.updateOngoingState(TmfStateValue.newValueInt(4), statusQuark);
            }
                break;
            case ZIPKIN_FINISH: {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, QUARK_MSG);
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, QUARK_STATUS);
                ss.modifyAttribute(ts, TmfStateValue.nullValue(), statusQuark);
            }
                break;
            case ZIPKIN_MSG_DESTRUCTED: {
                int pidQuark = ss.getQuarkRelativeAndAdd(hostQuark, pid.toString());
                int tidQuark = ss.getQuarkRelativeAndAdd(pidQuark, QUARK_MSG);
                int statusQuark = ss.getQuarkRelativeAndAdd(tidQuark, QUARK_STATUS);
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