/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.kafka;

import com.facebook.presto.Session;
import com.facebook.presto.kafka.util.CodecSupplier;
import com.facebook.presto.kafka.util.EmbeddedKafka;
import com.facebook.presto.kafka.util.TestUtils;
import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.metadata.QualifiedTableName;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.tests.DistributedQueryRunner;
import com.facebook.presto.tests.TestingPrestoClient;
import com.facebook.presto.tpch.TpchPlugin;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.airlift.tpch.TpchTable;

import java.util.Map;

import static com.facebook.presto.kafka.util.TestUtils.installKafkaPlugin;
import static com.facebook.presto.kafka.util.TestUtils.loadTpchTopicDescription;
import static com.facebook.presto.spi.type.TimeZoneKey.UTC_KEY;
import static com.facebook.presto.tpch.TpchMetadata.TINY_SCHEMA_NAME;
import static io.airlift.testing.Closeables.closeAllSuppress;
import static io.airlift.units.Duration.nanosSince;
import static java.util.Locale.ENGLISH;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class KafkaQueryRunner
{
    private KafkaQueryRunner()
    {
    }

    private static final Logger log = Logger.get("TestQueries");
    private static final String TPCH_SCHEMA = "tpch";

    public static DistributedQueryRunner createKafkaQueryRunner(EmbeddedKafka embeddedKafka, TpchTable<?>... tables)
            throws Exception
    {
        return createKafkaQueryRunner(embeddedKafka, ImmutableList.copyOf(tables));
    }

    public static DistributedQueryRunner createKafkaQueryRunner(EmbeddedKafka embeddedKafka, Iterable<TpchTable<?>> tables)
            throws Exception
    {
        DistributedQueryRunner queryRunner = null;
        try {
            queryRunner = new DistributedQueryRunner(createSession(), 2);

            queryRunner.installPlugin(new TpchPlugin());
            queryRunner.createCatalog("tpch", "tpch");

            embeddedKafka.start();

            for (TpchTable<?> table : tables) {
                embeddedKafka.createTopics(kafkaTopicName(table));
            }

            Map<SchemaTableName, KafkaTopicDescription> topicDescriptions = createTpchTopicDescriptions(queryRunner.getCoordinator().getMetadata(), tables);

            installKafkaPlugin(embeddedKafka, queryRunner, topicDescriptions);

            TestingPrestoClient prestoClient = queryRunner.getClient();

            log.info("Loading data...");
            long startTime = System.nanoTime();
            for (TpchTable<?> table : tables) {
                loadTpchTopic(embeddedKafka, prestoClient, table);
            }
            log.info("Loading complete in %s", nanosSince(startTime).toString(SECONDS));

            return queryRunner;
        }
        catch (Throwable e) {
            closeAllSuppress(e, queryRunner, embeddedKafka);
            throw e;
        }
    }

    private static void loadTpchTopic(EmbeddedKafka embeddedKafka, TestingPrestoClient prestoClient, TpchTable<?> table)
    {
        long start = System.nanoTime();
        log.info("Running import for %s", table.getTableName());
        TestUtils.loadTpchTopic(embeddedKafka, prestoClient, kafkaTopicName(table), new QualifiedTableName("tpch", TINY_SCHEMA_NAME, table.getTableName().toLowerCase(ENGLISH)));
        log.info("Imported %s in %s", 0, table.getTableName(), nanosSince(start).convertToMostSuccinctTimeUnit());
    }

    private static String kafkaTopicName(TpchTable<?> table)
    {
        return TPCH_SCHEMA + "." + table.getTableName().toLowerCase(ENGLISH);
    }

    private static Map<SchemaTableName, KafkaTopicDescription> createTpchTopicDescriptions(Metadata metadata, Iterable<TpchTable<?>> tables)
            throws Exception
    {
        JsonCodec<KafkaTopicDescription> topicDescriptionJsonCodec = new CodecSupplier<>(KafkaTopicDescription.class, metadata).get();

        ImmutableMap.Builder<SchemaTableName, KafkaTopicDescription> topicDescriptions = ImmutableMap.builder();
        for (TpchTable<?> table : tables) {
            String tableName = table.getTableName();
            SchemaTableName tpchTable = new SchemaTableName(TPCH_SCHEMA, tableName);

            topicDescriptions.put(loadTpchTopicDescription(topicDescriptionJsonCodec, tpchTable.toString(), tpchTable));
        }
        return topicDescriptions.build();
    }

    public static Session createSession()
    {
        return Session.builder()
                .setUser("user")
                .setSource("test")
                .setCatalog("kafka")
                .setSchema(TPCH_SCHEMA)
                .setTimeZoneKey(UTC_KEY)
                .setLocale(ENGLISH)
                .build();
    }
}
