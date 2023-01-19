package com.uid2.admin.job.jobsync.client;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.uid2.admin.job.jobsync.client.ClientKeySyncJob;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.factory.ClientKeyStoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.StoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ClientKeySyncJobTest {
    private InMemoryStorageMock cloudStorage;
    CloudPath globalSiteMetadataPath = new CloudPath("/some/test/path/clients/metadata.json");
    ObjectWriter objectWriter = JsonUtil.createJsonWriter();
    Integer scopedSiteId = 10;
    ImmutableList<OperatorKey> operators = ImmutableList.of(
            new OperatorKey(
                    "key",
                    "name",
                    "contact",
                    "protocol",
                    1618873215,
                    false,
                    scopedSiteId,
                    new HashSet<>(Collections.singletonList(Role.ID_READER)),
                    OperatorType.PRIVATE));

    ClientKey client = new ClientKey(
            "key",
            "secret",
            "name",
            "contact",
            Instant.MIN,
            ImmutableSet.of(Role.ID_READER),
            scopedSiteId,
            false);
    private ClientKeyStoreFactory clientKeyStoreFactory;

    @BeforeEach
    void setUp() {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        clientKeyStoreFactory = new ClientKeyStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                fileStorage,
                objectWriter,
                versionGenerator,
                clock);
    }

    @Test
    public void doesNotSyncClientsThatAreNotChanged() throws Exception {
        clientKeyStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(client), null);
        clientKeyStoreFactory.getGlobalWriter().upload(ImmutableList.of(client), null);

        StoreReader<Collection<ClientKey>> reader = clientKeyStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        ClientKeySyncJob job = new ClientKeySyncJob(new MultiScopeStoreWriter<>(
                clientKeyStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(client), operators
        );

        job.execute();

        reader.loadContent();
        assertThat(reader.getAll()).containsExactly(client);
        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isEqualTo(oldVersion);
    }

}