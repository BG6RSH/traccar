/*
 * Copyright 2026 Anton Tananaev (anton@traccar.org)
 *
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
package org.traccar.mapmatcher;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.helper.CoordinateUtil;
import org.traccar.model.Position;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AmapGraspRoadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmapGraspRoadService.class);

    private static final String DEFAULT_URL = "https://restapi.amap.com/v4/grasproad/driving";
    private static final int MAX_POINTS_PER_REQUEST = 500;

    private final Client client;
    private final String key;

    public AmapGraspRoadService(Client client, String key) {
        this.client = client;
        this.key = key;
    }

    public List<CorrectedPoint> correctPath(List<Position> positions) {
        if (positions == null || positions.size() < 2) {
            return List.of();
        }

        List<CorrectedPoint> result = new ArrayList<>();
        int totalBatches = (positions.size() + MAX_POINTS_PER_REQUEST - 1) / MAX_POINTS_PER_REQUEST;

        for (int batch = 0; batch < totalBatches; batch++) {
            int fromIndex = batch * MAX_POINTS_PER_REQUEST;
            int toIndex = Math.min(fromIndex + MAX_POINTS_PER_REQUEST, positions.size());
            List<Position> batchPositions = positions.subList(fromIndex, toIndex);

            List<CorrectedPoint> batchResult = correctBatch(batchPositions);
            if (batchResult.isEmpty()) {
                LOGGER.warn("Amap grasp road batch {} failed, returning partial results", batch + 1);
                break;
            }
            result.addAll(batchResult);
        }

        return result;
    }

    private List<CorrectedPoint> correctBatch(List<Position> positions) {
        JsonArrayBuilder bodyBuilder = Json.createArrayBuilder();
        long firstTime = positions.get(0).getFixTime().getTime() / 1000;

        for (Position p : positions) {
            CoordinateUtil.Coordinate gcj02 = CoordinateUtil.wgs84ToGcj02(
                    p.getLatitude(), p.getLongitude());
            long tm = p.getFixTime().getTime() / 1000 - firstTime;
            double rawSpeed = p.getSpeed();
            double rawCourse = p.getCourse();
            double sp = Double.isNaN(rawSpeed) ? 0 : rawSpeed * 3.6;
            double ag = Double.isNaN(rawCourse) ? 0 : rawCourse;
            bodyBuilder.add(Json.createObjectBuilder()
                    .add("x", round6(gcj02.longitude()))
                    .add("y", round6(gcj02.latitude()))
                    .add("ag", round1(ag))
                    .add("tm", tm)
                    .add("sp", round1(sp))
                    .build());
        }

        String body = bodyBuilder.build().toString();

        try {
            Response response = client
                    .target(DEFAULT_URL)
                    .queryParam("key", key)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(body, MediaType.APPLICATION_JSON));

            String responseBody = response.readEntity(String.class);

            List<CorrectedPoint> result = new ArrayList<>();
            try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
                JsonObject json = reader.readObject();

                String errcode = json.getString("errcode", "");
                if (!"0".equals(errcode) && !errcode.isEmpty()) {
                    String errmsg = json.getString("errmsg", "unknown error");
                    LOGGER.warn("Amap grasp road failed: errcode={}, errmsg={}", errcode, errmsg);
                    return List.of();
                }

                JsonObject data = json.getJsonObject("data");
                if (data == null) {
                    LOGGER.warn("Amap grasp road returned null data, response: {}", responseBody);
                    return List.of();
                }

                JsonArray points = data.getJsonArray("points");
                if (points == null || points.isEmpty()) {
                    LOGGER.warn("Amap grasp road returned empty points");
                    return List.of();
                }

                long baseTime = positions.get(0).getFixTime().getTime();
                for (int i = 0; i < points.size(); i++) {
                    JsonObject point = points.getJsonObject(i);
                    double x = point.getJsonNumber("x").doubleValue();
                    double y = point.getJsonNumber("y").doubleValue();
                    Date fixTime;
                    if (point.containsKey("tm") && point.getJsonNumber("tm") != null) {
                        long tm = point.getJsonNumber("tm").longValue();
                        fixTime = new Date(baseTime + tm * 1000);
                    } else if (i < positions.size()) {
                        fixTime = positions.get(i).getFixTime();
                    } else {
                        fixTime = positions.get(positions.size() - 1).getFixTime();
                    }
                    result.add(new CorrectedPoint(y, x, fixTime));
                }
            }

            return result;
        } catch (Exception e) {
            LOGGER.warn("Amap grasp road request exception: {}", e.getMessage());
            return List.of();
        }
    }

    private static double round6(double value) {
        return Math.round(value * 1000000.0) / 1000000.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record CorrectedPoint(double latitude, double longitude, Date fixTime) {
    }

}
