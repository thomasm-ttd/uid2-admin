package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import com.uid2.admin.store.FileName;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Collection;

public class ClientKeyStoreWriter  {
    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;

    public ClientKeyStoreWriter(RotatingClientKeyProvider provider, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("clients", ".json");
        FileName backupFile = new FileName("clients-old", ".json");
        String dataType = "client_keys";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, backupFile, dataType);
    }

    public void upload(Collection<ClientKey> data) throws Exception {
        writer.upload(jsonWriter.writeValueAsString(data));
    }
}
;