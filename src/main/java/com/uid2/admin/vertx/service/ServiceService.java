package com.uid2.admin.vertx.service;

import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.Service;
import com.uid2.shared.store.reader.RotatingServiceStore;
import com.uid2.shared.store.reader.RotatingSiteStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ServiceService implements IService {

    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final StoreWriter<Collection<Service>> storeWriter;
    private final RotatingServiceStore serviceProvider;
    private final RotatingSiteStore siteProvider;
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceService.class);

    public ServiceService(AuthMiddleware auth,
                          WriteLock writeLock,
                          StoreWriter<Collection<Service>> storeWriter,
                          RotatingServiceStore serviceProvider,
                          RotatingSiteStore siteProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.serviceProvider = serviceProvider;
        this.siteProvider = siteProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/service/list").handler(auth.handle(this::handleServiceListAll, Role.ADMINISTRATOR));
        router.get("/api/service/list/:service_id").handler(auth.handle(this::handleServiceList, Role.ADMINISTRATOR));
        router.post("/api/service/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleServiceAdd(ctx);
            }
        }, Role.ADMINISTRATOR));
        router.post("/api/service/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleUpdate(ctx);
            }
        }, Role.ADMINISTRATOR));
        router.post("/api/service/delete").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleDelete(ctx);
            }
        }, Role.ADMINISTRATOR));
    }

    private void handleServiceListAll(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            this.serviceProvider.getAllServices().forEach(s -> ja.add(toJson(s)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private void handleServiceList(RoutingContext rc) {
        final int serviceId;
        try {
            serviceId = Integer.parseInt(rc.pathParam("service_id"));
        } catch (Exception e) {
            ResponseUtil.error(rc, 400, "failed to parse a service_id from request");
            return;
        }

        Service service = serviceProvider.getService(serviceId);
        if (service == null) {
            ResponseUtil.error(rc, 404, "failed to find a service for service_id: " + serviceId);
            return;
        }

        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(toJson(service).encodePrettily());
    }

    private void handleServiceAdd(RoutingContext rc) {
        try {
            siteProvider.loadContent();

            JsonObject body = rc.body().asJsonObject();
            if (body == null) {
                ResponseUtil.error(rc, 400, "json payload required but not provided");
                return;
            }
            Integer siteId = body.getInteger("site_id");
            String name = body.getString("name");
            JsonArray rolesSpec = body.getJsonArray("roles");
            if (siteId == null || name == null || rolesSpec == null) {
                ResponseUtil.error(rc, 400, "required parameters: site_id, name, roles");
                return;
            }

            if (siteProvider.getSite(siteId) == null) {
                ResponseUtil.error(rc, 404, "site_id " + siteId + " not valid");
                return;
            }

            if (name.equals("")) {
                ResponseUtil.error(rc, 400, "name cannot be empty");
                return;
            }

            boolean exists = serviceProvider.getAllServices().stream().anyMatch(s -> s.getSiteId() == siteId && s.getName().equals(name));
            if (exists) {
                ResponseUtil.error(rc, 400, "site_id " + siteId + " already has service of name " + name);
                return;
            }

            final Set<Role> roles;
            try {
                roles = rolesSpec.stream().map(s -> Role.valueOf((String) s)).collect(Collectors.toSet());
            } catch (IllegalArgumentException e) {
                ResponseUtil.error(rc, 400, "invalid parameter: roles");
                return;
            }

            final List<Service> services = this.serviceProvider.getAllServices()
                    .stream().sorted(Comparator.comparingInt(Service::getServiceId))
                    .collect(Collectors.toList());
            final int serviceId = 1 + services.stream().mapToInt(Service::getServiceId).max().orElse(0);
            Service service = new Service(serviceId, siteId, name, roles);

            services.add(service);

            storeWriter.upload(services, null);

            rc.response().end(toJson(service).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    // Can update the site_id, name and roles
    private void handleUpdate(RoutingContext rc) {
        try {
            JsonObject body = rc.body().asJsonObject();
            if (body == null) {
                ResponseUtil.error(rc, 400, "json payload required but not provided");
                return;
            }
            Integer serviceId = body.getInteger("service_id");
            Integer siteId = body.getInteger("site_id");
            String name = body.getString("name");

            JsonArray rolesSpec = null;
            if (body.getString("roles") != null && !body.getString("roles").isEmpty()) {
                try {
                    rolesSpec = body.getJsonArray("roles");
                } catch (ClassCastException c) {
                    ResponseUtil.error(rc, 400, "invalid parameter: roles");
                    return;
                }
            }

            if (serviceId == null) {
                ResponseUtil.error(rc, 400, "required parameters: service_id");
                return;
            }

            final Service service = serviceProvider.getService(serviceId);
            if (service == null) {
                ResponseUtil.error(rc, 404, "failed to find a service for service_id: " + serviceId);
                return;
            }

            // check that this does not create a duplicate service
            if (siteId != null && siteId != 0 && name != null && !name.isEmpty()) {
                boolean exists = serviceProvider.getAllServices().stream().anyMatch(s -> s.getServiceId() != serviceId && s.getSiteId() == siteId && s.getName().equals(name));
                if (exists) {
                    ResponseUtil.error(rc, 400, "site_id " + siteId + " already has service of name " + name);
                    return;
                }
            }

            if (rolesSpec != null) {
                final Set<Role> roles;
                try {
                    roles = rolesSpec.stream().map(s -> Role.valueOf((String) s)).collect(Collectors.toSet());
                } catch (IllegalArgumentException e) {
                    ResponseUtil.error(rc, 400, "invalid parameter: roles");
                    return;
                }
                service.setRoles(roles);
            }

            if (siteId != null && siteId != 0) {
                service.setSiteId(siteId);
            }

            if (name != null && !name.isEmpty()) {
                service.setName(name);
            }

            final List<Service> services = this.serviceProvider.getAllServices()
                    .stream().sorted(Comparator.comparingInt(Service::getServiceId))
                    .collect(Collectors.toList());

            storeWriter.upload(services, null);

            rc.response().end(toJson(service).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private void handleDelete(RoutingContext rc) {
        final int serviceId;
        JsonObject body = rc.body() != null ? rc.body().asJsonObject() : null;
        if (body == null) {
            ResponseUtil.error(rc, 400, "json payload required but not provided");
            return;
        }
        serviceId = body.getInteger("service_id", -1);
        if (serviceId == -1) {
            ResponseUtil.error(rc, 400, "required parameters: service_id");
            return;
        }

        try {
            serviceProvider.loadContent();

            Service service = serviceProvider.getService(serviceId);
            if (service == null) {
                ResponseUtil.error(rc, 404, "failed to find a service for service_id: " + serviceId);
                return;
            }

            final List<Service> services = this.serviceProvider.getAllServices()
                    .stream().sorted(Comparator.comparingInt(Service::getServiceId))
                    .collect(Collectors.toList());

            services.remove(service);

            storeWriter.upload(services, null);

            rc.response().end(toJson(service).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }

    }

    private JsonObject toJson(Service s) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("service_id", s.getServiceId());
        jsonObject.put("site_id", s.getSiteId());
        jsonObject.put("name", s.getName());
        jsonObject.put("roles", s.getRoles());
        return jsonObject;
    }
}
