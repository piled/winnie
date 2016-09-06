package com.piled.winnie;

import java.util.List;

interface ItemProducer {
    List<Item> requestItems(double lat, double lng);
}
