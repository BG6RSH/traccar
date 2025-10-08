package org.traccar.geocoder;

import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;

public class QqGeocoder extends JsonGeocoder {
    private static String formatUrl(String url, String key) {
        if (url == null) {
            url = "https://apis.map.qq.com/ws/geocoder/v1";
        }

        if (key != null) {
            url += "?key=" + key + "&location=%f,%f";
        }
        return url;
    }

    public QqGeocoder(Client client, String url, String key, int cacheSize, AddressFormat addressFormat) {
        super(client, formatUrl(url, key), cacheSize, addressFormat);
    }

    @Override
    public Address parseAddress(JsonObject json) {
        Address address = new Address();
        try {
            String value = json.getJsonObject("result").getJsonObject("formatted_addresses").getString("recommend");
            address.setFormattedAddress(value);
            value = json.getJsonObject("result").getString("address");
            address.setStreet(value);
        } catch (Exception e) {
            return null;
        }
        return address;
    }
}