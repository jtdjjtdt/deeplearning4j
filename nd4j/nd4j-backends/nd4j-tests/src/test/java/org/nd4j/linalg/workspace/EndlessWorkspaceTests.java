/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */

package org.nd4j.linalg.workspace;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.BaseNd4jTestWithBackends;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.memory.conf.WorkspaceConfiguration;
import org.nd4j.linalg.api.memory.enums.LearningPolicy;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
@Slf4j
@Tag(TagNames.WORKSPACES)
@NativeTag
public class EndlessWorkspaceTests extends BaseNd4jTestWithBackends {
    DataType initialType = Nd4j.dataType();

    @BeforeEach
    public void startUp() {
        Nd4j.getMemoryManager().togglePeriodicGc(false);
    }

    @AfterEach
    public void shutUp() {
        Nd4j.getMemoryManager().setCurrentWorkspace(null);
        Nd4j.getWorkspaceManager().destroyAllWorkspacesForCurrentThread();
        Nd4j.setDataType(this.initialType);
        Nd4j.getMemoryManager().togglePeriodicGc(true);
    }

    /**
     * This test checks for allocations within single workspace, without any spills
     *
     * @throws Exception
     */
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void endlessTest1(Nd4jBackend backend) {

        Nd4j.getWorkspaceManager().setDefaultWorkspaceConfiguration(
                        WorkspaceConfiguration.builder().initialSize(100 * 1024L * 1024L).build());

        Nd4j.getMemoryManager().togglePeriodicGc(false);

        AtomicLong counter = new AtomicLong(0);
        while (true) {
            try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace()) {
                long time1 = System.nanoTime();
                INDArray array = Nd4j.create(1024 * 1024);
                long time2 = System.nanoTime();
                array.addi(1.0f);
                assertEquals(1.0f, array.meanNumber().floatValue(), 0.1f);

                if (counter.incrementAndGet() % 1000 == 0)
                    log.info("{} iterations passed... Allocation time: {} ns", counter.get(), time2 - time1);
            }
        }
    }

    /**
     * This test checks for allocation from workspace AND spills
     * @throws Exception
     */
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void endlessTest2(Nd4jBackend backend) {
        Nd4j.getWorkspaceManager().setDefaultWorkspaceConfiguration(
                        WorkspaceConfiguration.builder().initialSize(10 * 1024L * 1024L).build());

        Nd4j.getMemoryManager().togglePeriodicGc(false);

        AtomicLong counter = new AtomicLong(0);
        while (true) {
            try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace()) {
                long time1 = System.nanoTime();
                INDArray array = Nd4j.create(2 * 1024 * 1024);
                long time2 = System.nanoTime();
                array.addi(1.0f);
                assertEquals(1.0f, array.meanNumber().floatValue(), 0.1f);

                long time3 = System.nanoTime();
                INDArray array2 = Nd4j.create(3 * 1024 * 1024);
                long time4 = System.nanoTime();

                if (counter.incrementAndGet() % 1000 == 0) {
                    log.info("{} iterations passed... Allocation time: {} vs {} (ns)", counter.get(), time2 - time1,
                                    time4 - time3);
                    System.gc();
                }
            }
        }
    }

    /**
     * This endless test checks for nested workspaces and cross-workspace memory use
     *
     * @throws Exception
     */
    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void endlessTest3(Nd4jBackend backend) {
        Nd4j.getWorkspaceManager().setDefaultWorkspaceConfiguration(
                        WorkspaceConfiguration.builder().initialSize(10 * 1024L * 1024L).build());

        Nd4j.getMemoryManager().togglePeriodicGc(false);
        AtomicLong counter = new AtomicLong(0);
        while (true) {
            try (MemoryWorkspace workspace1 = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS_1")) {
                INDArray array1 = Nd4j.create(2 * 1024 * 1024);
                array1.assign(1.0);

                try (MemoryWorkspace workspace2 = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS_2")) {
                    INDArray array2 = Nd4j.create(2 * 1024 * 1024);
                    array2.assign(1.0);

                    array1.addi(array2);

                    assertEquals(2.0f, array1.meanNumber().floatValue(), 0.01);

                    if (counter.incrementAndGet() % 1000 == 0) {
                        log.info("{} iterations passed...", counter.get());
                        System.gc();
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void endlessTest4(Nd4jBackend backend) {
        Nd4j.getWorkspaceManager().setDefaultWorkspaceConfiguration(
                        WorkspaceConfiguration.builder().initialSize(100 * 1024L * 1024L).build());
        while (true) {
            try (MemoryWorkspace workspace1 = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS_1")) {
                for (int i = 0; i < 1000; i++) {
                    INDArray array = Nd4j.createUninitialized(RandomUtils.nextInt(1, 50), RandomUtils.nextInt(1, 50));

                    INDArray mean = array.max(1);
                }

                for (int i = 0; i < 1000; i++) {
                    INDArray array = Nd4j.createUninitialized(RandomUtils.nextInt(1, 100));

                    array.maxNumber().doubleValue();
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void endlessTest5(Nd4jBackend backend) throws Exception {
        while (true) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    WorkspaceConfiguration wsConf = WorkspaceConfiguration.builder().initialSize(10 * 1024L * 1024L)
                                    .policyLearning(LearningPolicy.NONE).build();

                    try (MemoryWorkspace ws = Nd4j.getWorkspaceManager().getAndActivateWorkspace(wsConf, "PEW-PEW")) {
                        INDArray array = Nd4j.create(10);
                    }
                }
            });

            thread.start();
            thread.join();

            System.gc();
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void endlessTest6(Nd4jBackend backend) {
        Nd4j.getMemoryManager().togglePeriodicGc(false);
        WorkspaceConfiguration wsConf = WorkspaceConfiguration.builder().initialSize(10 * 1024L * 1024L)
                        .policyLearning(LearningPolicy.NONE).build();
        final AtomicLong cnt = new AtomicLong(0);
        while (true) {

            try (MemoryWorkspace ws = Nd4j.getWorkspaceManager().getAndActivateWorkspace(wsConf, "PEW-PEW")) {
                INDArray array = Nd4j.create(new float[] {1f, 2f, 3f, 4f, 5f});
            }

            if (cnt.incrementAndGet() % 1000000 == 0)
                log.info("TotalBytes: {}", Pointer.totalBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")

    public void endlessValidation1(Nd4jBackend backend) {
        Nd4j.getMemoryManager().togglePeriodicGc(true);

        AtomicLong counter = new AtomicLong(0);
        while (true) {
            INDArray array1 = Nd4j.create(2 * 1024 * 1024);
            array1.assign(1.0);

            assertEquals(1.0f, array1.meanNumber().floatValue(), 0.01);

            if (counter.incrementAndGet() % 1000 == 0) {
                log.info("{} iterations passed...", counter.get());
                System.gc();
            }
        }
    }


    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void testPerf1(Nd4jBackend backend) {
        Nd4j.getWorkspaceManager()
                        .setDefaultWorkspaceConfiguration(WorkspaceConfiguration.builder().initialSize(50000L).build());

        MemoryWorkspace ws = Nd4j.getWorkspaceManager().getWorkspaceForCurrentThread("WS_1");

        INDArray tmp = Nd4j.create(64 * 64 + 1);

        //Nd4j.getMemoryManager().togglePeriodicGc(true);

        List<Long> results = new ArrayList<>();
        List<Long> resultsOp = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            long time1 = System.nanoTime();
            long time3 = 0;
            long time4 = 0;
            //MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS_1");
            try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace("WS_1")) {
                INDArray array = Nd4j.createUninitialized(64 * 64 + 1);
                INDArray arrayx = Nd4j.createUninitialized(64 * 64 + 1);

                time3 = System.nanoTime();
                arrayx.addi(1.01);
                time4 = System.nanoTime();

            }
            //workspace.notifyScopeLeft();
            long time2 = System.nanoTime();

            results.add(time2 - time1);
            resultsOp.add(time4 - time3);
        }
        Collections.sort(results);
        Collections.sort(resultsOp);

        int pos = (int) (results.size() * 0.9);

        log.info("Block: {} ns; Op: {} ns;", results.get(pos), resultsOp.get(pos));
    }

    @ParameterizedTest
    @MethodSource("org.nd4j.linalg.BaseNd4jTestWithBackends#configs")
    public void endlessTestSerDe1(Nd4jBackend backend) throws Exception {
        INDArray features = Nd4j.create(32, 3, 224, 224);
        INDArray labels = Nd4j.create(32, 200);
        File tmp = File.createTempFile("12dadsad", "dsdasds");
        float[] array = new float[33 * 3 * 224 * 224];
        DataSet ds = new DataSet(features, labels);
        ds.save(tmp);

        WorkspaceConfiguration wsConf = WorkspaceConfiguration.builder().initialSize(0)
                        .policyLearning(LearningPolicy.FIRST_LOOP).build();

        while (true) {

            try (MemoryWorkspace workspace = Nd4j.getWorkspaceManager().getAndActivateWorkspace(wsConf, "serde")) {
                /*
                            try (FileOutputStream fos = new FileOutputStream(tmp); BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                SerializationUtils.serialize(array, fos);
                            }
                
                            try (FileInputStream fis = new FileInputStream(tmp); BufferedInputStream bis = new BufferedInputStream(fis)) {
                long time1 = System.currentTimeMillis();
                float[] arrayR = (float[]) SerializationUtils.deserialize(bis);
                long time2 = System.currentTimeMillis();
                
                log.info("Load time: {}", time2 - time1);
                            }
                */



                long time1 = System.currentTimeMillis();
                ds.load(tmp);
                long time2 = System.currentTimeMillis();

                log.info("Load time: {}", time2 - time1);
            }
        }
    }

    @Override
    public char ordering() {
        return 'c';
    }
}
