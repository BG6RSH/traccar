/*
 * Copyright 2017 Jan-Piet Mens (jpmens@gmail.com)
 * Copyright 2017 - 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.protocol;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.session.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class OwnTracksProtocolDecoder extends BaseHttpProtocolDecoder {

    public OwnTracksProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        FullHttpRequest request = (FullHttpRequest) msg;
        JsonObject root = Json.createReader(
                new StringReader(request.content().toString(StandardCharsets.US_ASCII))).readObject();

        if (!root.getString("_type").equals("location")) {
            sendResponse(channel, HttpResponseStatus.OK);
            return null;
        }

        String uniqueId = root.getString("tid");
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, uniqueId);
        if (deviceSession == null) {
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        position.setTime(new Date(root.getJsonNumber("tst").longValue() * 1000));
        if (root.containsKey("sent")) {
            position.setDeviceTime(new Date(root.getJsonNumber("sent").longValue() * 1000));
        }

        position.setValid(true);

        position.setLatitudeWgs84(root.getJsonNumber("lat").doubleValue());
        position.setLongitudeWgs84(root.getJsonNumber("lon").doubleValue());

        if (root.containsKey("vel")) {
            position.setSpeed(UnitsConverter.knotsFromKph(root.getInt("vel")));
        }
        if (root.containsKey("alt")) {
            position.setAltitude(root.getInt("alt"));
        }
        if (root.containsKey("cog")) {
            position.setCourse(root.getInt("cog"));
        }
        if (root.containsKey("acc")) {
            position.setAccuracy(root.getInt("acc"));
        }
        if (root.containsKey("t")) {
            String event = root.getString("t");
            decodeAlarm(position, event, root.getInt("rty", -1));
            position.set(Position.KEY_EVENT, event);
        }
        if (root.containsKey("batt")) {
            position.set(Position.KEY_BATTERY_LEVEL, root.getInt("batt"));
        }
        if (root.containsKey("uext")) {
            position.set(Position.KEY_POWER, root.getJsonNumber("uext").doubleValue());
        }
        if (root.containsKey("ubatt")) {
            position.set(Position.KEY_BATTERY, root.getJsonNumber("ubatt").doubleValue());
        }
        if (root.containsKey("vin")) {
            position.set(Position.KEY_VIN, root.getString("vin"));
        }
        if (root.containsKey("name")) {
            position.set(Position.KEY_VIN, root.getString("name"));
        }
        if (root.containsKey("rpm")) {
            position.set(Position.KEY_RPM, root.getInt("rpm"));
        }
        if (root.containsKey("ign")) {
            position.set(Position.KEY_IGNITION, root.getBoolean("ign"));
        }
        if (root.containsKey("motion")) {
            position.set(Position.KEY_MOTION, root.getBoolean("motion"));
        }
        if (root.containsKey("odometer")) {
            position.set(Position.KEY_ODOMETER, root.getJsonNumber("odometer").doubleValue() * 1000.0);
        }
        if (root.containsKey("hmc")) {
            position.set(Position.KEY_HOURS, root.getJsonNumber("hmc").doubleValue() / 3600.0);
        }

        if (root.containsKey("anum")) {
            int inputCount = root.getInt("anum");
            for (int i = 0; i < inputCount; i++) {
                String indexString = String.format("%02d", i);
                if (root.containsKey("adda-" + indexString)) {
                    position.set(Position.PREFIX_ADC + (i + 1), root.getString("adda-" + indexString));
                }
                if (root.containsKey("temp_c-" + indexString)) {
                    position.set(Position.PREFIX_TEMP + (i + 1),
                        root.getJsonNumber("temp_c-" + indexString).doubleValue());
                }
            }
        }

        sendResponse(channel, HttpResponseStatus.OK);
        return position;
    }

    private void decodeAlarm(Position position, String event, Integer reportType) {
        switch (event) {
            case "9" -> position.addAlarm(Position.ALARM_LOW_BATTERY);
            case "1" -> position.addAlarm(Position.ALARM_POWER_ON);
            case "i" -> position.set(Position.KEY_IGNITION, true);
            case "I" -> position.set(Position.KEY_IGNITION, false);
            case "E" -> position.addAlarm(Position.ALARM_POWER_RESTORED);
            case "e" -> position.addAlarm(Position.ALARM_POWER_CUT);
            case "!" -> position.addAlarm(Position.ALARM_TOW);
            case "s" -> position.addAlarm(Position.ALARM_OVERSPEED);
            case "h" -> {
                switch (reportType) {
                    case 0, 3 -> position.addAlarm(Position.ALARM_BRAKING);
                    case 1, 4 -> position.addAlarm(Position.ALARM_ACCELERATION);
                    case 2, 5 -> position.addAlarm(Position.ALARM_CORNERING);
                }
            }
        }
    }
}
