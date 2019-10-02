package org.eclipse.tracecompass.incubator.internal.ceph.core.stateprovider;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.DefaultEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * @author hdaoud
 *
 */
public class CephAnalysisModule extends TmfStateSystemAnalysisModule {

    /**
     * Analysis ID, it should match that in the plugin.xml file
     */
    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.java.analysis.core.cephanalysismodule"; //$NON-NLS-1$

    @SuppressWarnings("null")
    @Override
    protected ITmfStateProvider createStateProvider() {
        IKernelAnalysisEventLayout layout;
        layout = DefaultEventLayout.getInstance();
        ITmfTrace trace = getTrace();
        return new CephStateProvider(trace,layout);
    }
}