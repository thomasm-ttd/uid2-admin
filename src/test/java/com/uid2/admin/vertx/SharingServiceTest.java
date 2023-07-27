package com.uid2.admin.vertx;

import com.uid2.admin.model.Site;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SharingService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SharingServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new SharingService(auth, writeLock, keysetStoreWriter, keysetProvider, keysetKeyManager, siteProvider);
    }

    private void compareKeysetToResult(Keyset keyset, JsonArray actualKeyset) {
        assertNotNull(actualKeyset);
        Set<Integer> actualSet = actualKeyset.stream()
                .map(s -> (Integer) s)
                .collect(Collectors.toSet());
        assertEquals(keyset.getAllowedSites(), actualSet);
    }


    @Test
    void listSiteGet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/list/5", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareKeysetToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowlist"));

            Integer expectedHash = keysets.get(1).hashCode();
            assertEquals(expectedHash, response.bodyAsJsonObject().getInteger("hash"));

            testContext.completeNow();
        });
    }

    @Test
    void listSiteGetNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/list/42", ar -> {
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void listSiteSet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        post(vertx, "api/sharing/list/5", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": 0\n" +
                "  }";

        post(vertx, "api/sharing/list/8", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(4, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());

            //Ensure new key was created
            try {
                verify(keysetKeyManager).addKeysetKey(4);
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetConcurrency(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body1 = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        String body2 = "  {\n" +
                "    \"allowlist\": [\n" +
                "      2,\n" +
                "      5,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        post(vertx, "api/sharing/list/5", body1, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("whitelist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });

        post(vertx, "api/sharing/list/5", body2, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(409, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void listAll(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/lists", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetToResult(keysets.get(keyset_id), resp.getJsonArray("allowlist"));

                Integer expectedHash = keysets.get(keyset_id).hashCode();
                assertEquals(expectedHash, resp.getInteger("hash"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/keyset/1", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareKeysetToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowlist"));

            testContext.completeNow();
        });
    }

    @Test
    void listAllKeysets(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/keysets", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetToResult(keysets.get(keyset_id), resp.getJsonArray("allowlist"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(5, "test-name", true)).when(siteProvider).getSite(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1," +
                "    \"site_id\": 5," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(null).when(siteProvider).getSite(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1," +
                "    \"site_id\": 5," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Site id 5 not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNoSiteIdOrKeysetId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("You must specify at least one of: keyset_id, site_id", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetWithoutNameUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(5, "test-name", true)).when(siteProvider).getSite(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test-name", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1\n" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test-name", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(4, 8, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewIdenticalNameAndSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test-name", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 8, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Keyset with same site_id and name already exists", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewSameNameDifferentSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(2).getAllowedSites());
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, -1, 2})
    void KeysetSetNewReservedSite(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [],\n" +
                String.format("    \"site_id\": %d,", input) +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Site id " + input + " not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, -1, 2})
    void KeysetSetUpdateReservedSite(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [],\n" +
                String.format("    \"site_id\": %d,", input) +
                "    \"name\": \"test-name\"," +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Site id " + input + " not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewEmptyAllowlist(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 8, "test", Set.of(), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(2).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateEmptyAllowlist(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(5, "test", true)).when(siteProvider).getSite(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [],\n" +
                "    \"name\": \"test-name\"," +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", Set.of(), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetDisallowSelf(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [8, 5],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 8, "test", Set.of(5), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(2).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetDisallowDuplicates(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [8, 8],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Duplicate site_id not permitted", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }
}
