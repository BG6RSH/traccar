/*
* 高德地图逆地址转换
* Copyright 2015 Bgrsh (576998@qq.com)
*/
package org.traccar.geocoder;

import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;

public class AmapGeocoder extends JsonGeocoder {

   private static String formatUrl(String url, String key) {
       if (url == null) {
           url = "https://restapi.amap.com/v3/geocode/regeo";
       }
       url += "?location=%f,%f&roadlevel=0&key=" + key;

       return url;
   }

   public AmapGeocoder(Client client, String url, String key, int cacheSize, AddressFormat addressFormat) {
       super(client, formatUrl(url, key), cacheSize, addressFormat);
   }

   @Override
   public Address parseAddress(JsonObject json) {
       Address address = new Address();
       try {
           String formattedAddress = json.getJsonObject("regeocode").getString("formattedAddress");
           address.setFormattedAddress(formattedAddress);
       } catch (Exception e) {
           return null;
       }
       return address;
   }
}