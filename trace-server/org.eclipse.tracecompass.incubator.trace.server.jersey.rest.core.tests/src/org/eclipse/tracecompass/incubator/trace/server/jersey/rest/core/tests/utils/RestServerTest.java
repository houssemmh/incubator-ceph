/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.utils;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.tracecompass.incubator.internal.trace.server.jersey.rest.core.model.views.QueryParameters;
import org.eclipse.tracecompass.incubator.internal.trace.server.jersey.rest.core.webapp.WebApplication;
import org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.stubs.DataProviderDescriptorStub;
import org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.stubs.ExperimentModelStub;
import org.eclipse.tracecompass.incubator.trace.server.jersey.rest.core.tests.stubs.TraceModelStub;
import org.eclipse.tracecompass.testtraces.ctf.CtfTestTrace;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor.ProviderType;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.ImmutableList;

/**
 * Rest server test abstract class, handles starting the server in test mode,
 * getting the correct client, and closing the traces and server once the test
 * is complete
 *
 * @author Loic Prieur-Drevon
 */
public abstract class RestServerTest {
    private static final String SERVER = "http://localhost:8378/tsp/api"; //$NON-NLS-1$
    private static final WebApplication fWebApp = new WebApplication(WebApplication.TEST_PORT);
    /**
     * Traces endpoint path (relative to application).
     */
    public static final String TRACES = "traces";
    /**
     * Experiments endpoint path (relative to application).
     */
    public static final String EXPERIMENTS = "experiments";

    /**
     * Outputs path segment
     */
    public static final String OUTPUTS_PATH = "outputs";

    /**
     * Tree path segment
     */
    public static final String TREE_PATH = "tree";

    /**
     * Time Graph path segment
     */
    public static final String TIMEGRAPH_PATH = "timeGraph";

    /**
     * XY path segment
     */
    public static final String XY_PATH = "XY";

    /**
     * <b>name</b> constant
     */
    public static final String NAME = "name";
    /**
     * <b>path</b> constant
     */
    public static final String URI = "uri";

    private static final GenericType<Set<TraceModelStub>> TRACE_MODEL_SET_TYPE = new GenericType<Set<TraceModelStub>>() {
    };
    private static final GenericType<Set<ExperimentModelStub>> EXPERIMENT_MODEL_SET_TYPE = new GenericType<Set<ExperimentModelStub>>() {
    };
    private static final GenericType<Set<DataProviderDescriptorStub>> DATAPROVIDER_DESCR_MODEL_SET_TYPE = new GenericType<Set<DataProviderDescriptorStub>>() {
    };

    /**
     * {@link UUID} for {@link CtfTestTrace#CONTEXT_SWITCHES_UST}.
     */
    protected static final UUID CONTEXT_SWITCHES_UST_UUID = UUID.fromString("8160c5b3-c482-4d86-9d81-3272e872537f");
    /**
     * {@link TraceModelStub} to represent the object returned by the server for
     * {@link CtfTestTrace#CONTEXT_SWITCHES_UST}.
     */
    protected static TraceModelStub CONTEXT_SWITCHES_UST_STUB;

    /**
     * {@link UUID} for {@link CtfTestTrace#CONTEXT_SWITCHES_KERNEL}.
     */
    protected static final UUID CONTEXT_SWITCHES_KERNEL_UUID = UUID.fromString("5694cebc-b3d1-2d46-a2e6-c6993919ae4f");
    /**
     * {@link TraceModelStub} to represent the object returned by the server for
     * {@link CtfTestTrace#CONTEXT_SWITCHES_KERNEL}.
     */
    protected static TraceModelStub CONTEXT_SWITCHES_KERNEL_STUB;

    /**
     * Expected toString() of all data providers for this experiment
     */
    protected static List<DataProviderDescriptorStub> EXPECTED_DATA_PROVIDER_DESCRIPTOR = null;

    /**
     * Create the {@link TraceModelStub}s before running the tests
     *
     * @throws IOException
     *             if the URL could not be converted to a path
     */
    @BeforeClass
    public static void beforeTest() throws IOException {
        String contextSwitchesUstPath = FileLocator.toFileURL(CtfTestTrace.CONTEXT_SWITCHES_UST.getTraceURL()).getPath();
        CONTEXT_SWITCHES_UST_STUB = new TraceModelStub("ust", contextSwitchesUstPath, CONTEXT_SWITCHES_UST_UUID);

        String contextSwitchesKernelPath = FileLocator.toFileURL(CtfTestTrace.CONTEXT_SWITCHES_KERNEL.getTraceURL()).getPath();
        CONTEXT_SWITCHES_KERNEL_STUB = new TraceModelStub("kernel", contextSwitchesKernelPath, CONTEXT_SWITCHES_KERNEL_UUID);
        ImmutableList.Builder<DataProviderDescriptorStub> b = ImmutableList.builder();
        b.add(new DataProviderDescriptorStub("org.eclipse.tracecompass.internal.analysis.timing.core.segmentstore.scatter.dataprovider:org.eclipse.linuxtools.lttng2.ust.analysis.callstack",
                "LTTng-UST CallStack - Latency vs Time",
                "Show latencies provided by Analysis module: LTTng-UST CallStack",
                ProviderType.TREE_TIME_XY.name()));
        b.add(new DataProviderDescriptorStub("org.eclipse.tracecompass.internal.analysis.profiling.callstack.provider.CallStackDataProvider",
                "Flame Chart",
                "Show a call stack over time",
                ProviderType.TIME_GRAPH.name()));
        b.add(new DataProviderDescriptorStub("org.eclipse.tracecompass.internal.tmf.core.histogram.HistogramDataProvider",
                "Histogram",
                "Show a histogram of number of events to time for a trace",
                ProviderType.TREE_TIME_XY.name()));
        EXPECTED_DATA_PROVIDER_DESCRIPTOR = b.build();
    }

    /**
     * Start the Eclipse / Jetty Web server
     *
     * @throws Exception
     *             if there is a problem running this application.
     */
    @Before
    public void startServer() throws Exception {
        fWebApp.start();
    }

    /**
     * Stop the server once tests are finished, and close the traces
     */
    @After
    public void stopServer() {
        WebTarget application = getApplicationEndpoint();
        WebTarget experimentsTarget = application.path(EXPERIMENTS);
        for (ExperimentModelStub experiment: getExperiments(experimentsTarget)) {
            assertEquals(experiment, experimentsTarget.path(experiment.getUUID().toString()).request().delete().readEntity(ExperimentModelStub.class));
        }
        WebTarget traceTarget = application.path(TRACES);
        for (TraceModelStub trace : getTraces(traceTarget)) {
            assertEquals(trace, traceTarget.path(trace.getUUID().toString()).request().delete().readEntity(TraceModelStub.class));
        }
        assertEquals(Collections.emptySet(), getTraces(traceTarget));
        assertEquals(Collections.emptySet(), getExperiments(experimentsTarget));
        fWebApp.stop();
    }

    /**
     * Getter for the {@link WebTarget} for the application endpoint.
     *
     * @return the application endpoint {@link WebTarget}.
     */
    public static WebTarget getApplicationEndpoint() {
        Client client = ClientBuilder.newClient();
        client.register(JacksonJsonProvider.class);
        return client.target(SERVER);
    }

    /**
     * Get the {@link WebTarget} for the time graph tree endpoint.
     *
     * @param UUID
     *            Trace or experiment UUID
     * @param dataProviderId
     *            Data provider ID
     * @return The time graph tree endpoint
     */
    public static WebTarget getTimeGraphTreeEndpoint(String UUID, String dataProviderId) {
        return getApplicationEndpoint().path(EXPERIMENTS)
                .path(UUID)
                .path(OUTPUTS_PATH)
                .path(TIMEGRAPH_PATH)
                .path(dataProviderId)
                .path(TREE_PATH);
    }

    /**
     * Get the {@link WebTarget} for the XY tree endpoint.
     *
     * @param UUID
     *            Trace or experiment UUID
     * @param dataProviderId
     *            Data provider ID
     * @return The XY tree endpoint
     */
    public static WebTarget getXYTreeEndpoint(String UUID, String dataProviderId) {
        return getApplicationEndpoint().path(EXPERIMENTS)
                .path(UUID)
                .path(OUTPUTS_PATH)
                .path(XY_PATH)
                .path(dataProviderId)
                .path(TREE_PATH);
    }

    /**
     * Get the traces currently open on the server.
     *
     * @param traces
     *            traces endpoint on the server
     * @return list of currently open traces.
     */
    public static Set<TraceModelStub> getTraces(WebTarget traces) {
        return traces.request(MediaType.APPLICATION_JSON).get(TRACE_MODEL_SET_TYPE);
    }

    /**
     * Get the experiments currently open on the server.
     *
     * @param experiments
     *            experiment endpoint on the server.
     * @return list of currently open experiments.
     */
    public static Set<ExperimentModelStub> getExperiments(WebTarget experiments) {
        return experiments.request(MediaType.APPLICATION_JSON).get(EXPERIMENT_MODEL_SET_TYPE);
    }

    /**
     * Get a set of {@link DataProviderDescriptorStub}
     *
     * @param outputs
     *            {@link WebTarget} for the outputs endpoint
     * @return Set of {@link DataProviderDescriptorStub} given by the server
     */
    public static Set<DataProviderDescriptorStub> getDataProviderDescriptors(WebTarget outputs) {
        return outputs.request(MediaType.APPLICATION_JSON).get(DATAPROVIDER_DESCR_MODEL_SET_TYPE);
    }

    /**
     * Post the trace from an expected {@link TraceModelStub}, ensure that the post
     * returned correctly and that the returned model was that of the expected stub.
     *
     * @param traces
     *            traces endpoint
     * @param stub
     *            expected trace stub
     */
    public static void assertPost(WebTarget traces, TraceModelStub stub) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(NAME, stub.getName());
        parameters.put(URI, stub.getPath());
        Response response = traces.request().post(Entity.json(new QueryParameters(parameters , Collections.emptyList())));
        int code = response.getStatus();
        assertEquals("Failed to POST " + stub.getName() + ", error code=" + code, 200, code);
        assertEquals(stub, response.readEntity(TraceModelStub.class));
    }
}
