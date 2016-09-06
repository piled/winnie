package com.piled.winnie;

import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import android.util.JsonReader;
import android.util.Log;

public class FoursquareProducer implements ItemProducer {
    
    private final static String TAG = "winnie::producer";

    private final static String CLIENT_ID = "KB45V00TQB1PCVWOWRWQP3VPYAAB15BEG5VCZVGA3LADGA4B";
    private final static String CLIENT_SECRET = "J3R4VAADPG4N4IATDCA2NVOU1Q0FQ5LQ0ZS3TBNTFM2DVKFT";
    private final static String BASE_URL_PREFIX = "https://api.foursquare.com/v2/venues/explore?ll=";
    private final static String BASE_URL_SUFFIX = "&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET + "&v=20160903";
    
    public List<Item> requestItems(double lat, double lng) {
        //Log.d(TAG, "requestItems(): @" + lat + "," + lng);
        URLConnection urlConnection = null;
        try {
            URL url = new URL(BASE_URL_PREFIX + lat + "," + lng + BASE_URL_SUFFIX);
            urlConnection = url.openConnection();
            JsonReader reader = new JsonReader(new InputStreamReader(new BufferedInputStream(urlConnection.getInputStream()), "UTF-8"));
            //Log.d(TAG, "JsonReader");
            ArrayList<Item> items = new ArrayList<Item>();
            reader.setLenient(true);
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("response")) {
                    //Log.d(TAG, "response");
                    reader.beginObject();
                    while (reader.hasNext()) {
                        name = reader.nextName();
                        if (name.equals("groups")) {
                            parseGroups(reader, items);
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return items;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch(IOException e){
            e.printStackTrace();
        }
        return null;
    }

    // TODO: generalize parsing functions further (into array)
    
    private void parseGroups(JsonReader reader, List<Item> items) throws IOException {
        //Log.d(TAG, "groups");
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                //Log.d(TAG, "groups: " + name);
                if (name.equals("items")) {
                    parseItems(reader, items);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        reader.endArray();
    }
    
    private void parseItems(JsonReader reader, List<Item> items) throws IOException {
        //Log.d(TAG, "items: ");
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("venue")) {
                    parseVenue(reader, items);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        reader.endArray();
    }
    
    private void parseVenue(JsonReader reader, List<Item> items) throws IOException {
        Log.d(TAG, "");
        Item item = new Item();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                item.name = reader.nextString();
            } else if (name.equals("location")) {
                reader.beginObject();
                while (reader.hasNext()) {
                    name = reader.nextName();
                    if (name.equals("lat")) {
                        item.latitude = reader.nextDouble();
                    } else if (name.equals("lng")) {
                        item.longitude = reader.nextDouble();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if (name.equals("categories")) {
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        name = reader.nextName();
                        if (name.equals("shortName")) {
                            item.category = reader.nextString();
                        } else if (name.equals("icon")) {
                            reader.beginObject();
                            String prefix = null;
                            String suffix = null;
                            while (reader.hasNext()) {
                                name = reader.nextName();
                                if (name.equals("prefix")) {
                                    prefix = reader.nextString();
                                } else if (name.equals("suffix")) {
                                    suffix = reader.nextString();
                                } else {
                                    reader.skipValue();
                                }
                            }
                            if (prefix != null && suffix != null) {
                                item.icon = prefix + "bg_64" + suffix;
                            }
                            reader.endObject();
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (item.name != null && item.latitude != 0 && item.longitude != 0) {
            Log.d(TAG, "New item " + item.name + " @" + item.latitude + "," + item.longitude + " " + item.category + " / " + item.icon);
            ItemLoader.needItem(item);
            items.add(item);
        }
    }
}
