/*
 * Copyright 2013 - 2018 Anton Tananaev (anton@traccar.org)
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
import org.traccar.BaseProtocolDecoder;
import org.traccar.session.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.util.regex.Pattern;

public class ManPowerProtocolDecoder extends BaseProtocolDecoder {

    public ManPowerProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    private static final Pattern PATTERN = new PatternBuilder()
            .text("simei:")
            .number("(d+),")                     // imei
            .expression("[^,]*,[^,]*,")
            .expression("([^,]*),")              // status
            .number("d+,d+,d+.?d*,")
            .number("(dd)(dd)(dd)")              // date (yymmdd)
            .number("(dd)(dd)(dd),")             // time (hhmmss)
            .expression("([AV]),")               // validity
            .number("(dd)(dd.dddd),")            // latitude
            .expression("([NS]),")
            .number("(ddd)(dd.dddd),")           // longitude
            .expression("([EW])?,")
            .number("(d+.?d*),")                 // speed
            .any()
            .compile();

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        Parser parser = new Parser(PATTERN, (String) msg);
        if (!parser.matches()) {
            return null;
        }

        Position position = new Position(getProtocolName());

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, parser.next());
        if (deviceSession == null) {
            return null;
        }
        position.setDeviceId(deviceSession.getDeviceId());

        position.set(Position.KEY_STATUS, parser.next());

        position.setTime(parser.nextDateTime());

        position.setValid(parser.next().equals("A"));
        position.setLatitudeWgs84(parser.nextCoordinate());
        position.setLongitudeWgs84(parser.nextCoordinate());
        position.setSpeed(parser.nextDouble(0));

        return position;
    }

}
