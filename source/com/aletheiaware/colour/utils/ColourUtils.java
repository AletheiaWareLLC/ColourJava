/*
 * Copyright 2019 Aletheia Ware LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aletheiaware.colour.utils;

import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.EntryCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.PublicKeyFormat;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.BCProto.Reference;
import com.aletheiaware.bc.BCProto.SignatureAlgorithm;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.colour.ColourProto.Canvas;
import com.aletheiaware.colour.ColourProto.Colour;
import com.aletheiaware.colour.ColourProto.Location;
import com.aletheiaware.colour.ColourProto.Purchase;
import com.aletheiaware.colour.ColourProto.Vote;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class ColourUtils {

    public static final String TAG = "Colour";

    public static final String COLOUR_HOST = "colour.aletheiaware.com";
    public static final String COLOUR_HOST_TEST = "test-colour.aletheiaware.com";
    public static final String COLOUR_WEBSITE = "https://colour.aletheiaware.com";
    public static final String COLOUR_WEBSITE_TEST = "https://test-colour.aletheiaware.com";

    public static final String COLOUR_PREFIX_CANVAS = "Colour-Canvas-";
    public static final String COLOUR_PREFIX_PURCHASE = "Colour-Purchase-";
    public static final String COLOUR_PREFIX_VOTE = "Colour-Vote-";

    private ColourUtils() {}

    public static String dimenToString(Canvas canvas) {
        return canvas.getWidth() + "x" + canvas.getHeight() + "x" + canvas.getDepth();
    }

    /**
     * Sorts a list of Record hashes by the timestamp of the Meta they map to.
     */
    public static void sort(List<ByteString> hashes, Map<ByteString, Long> timestamps, boolean chronologically) {
        Collections.sort(hashes, new Comparator<ByteString>() {
            @Override
            public int compare(ByteString b1, ByteString b2) {
                long t1 = 0L;
                if (timestamps.containsKey(b1)) {
                    t1 = timestamps.get(b1);
                }
                long t2 = 0L;
                if (timestamps.containsKey(b2)) {
                    t2 = timestamps.get(b2);
                }
                if (chronologically) {
                    return Long.compare(t1, t2);
                }
                return Long.compare(t2, t1);
            }
        });
    }

    public static Canvas getCanvas(Channel canvases, byte[] recordHash) throws IOException {
        final Canvas[] canvas = new Canvas[1];
        canvases.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                if (Arrays.equals(entry.getRecordHash().toByteArray(), recordHash)) {
                    try {
                        canvas[0] = Canvas.newBuilder()
                                .mergeFrom(entry.getRecord().getPayload())
                                .build();
                        return true;
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }
        });
        return canvas[0];
    }

    public static List<Canvas> getCanvases(Channel canvases) throws IOException {
        List<Canvas> cs = new ArrayList<>();
        canvases.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                Record r = entry.getRecord();
                Canvas.Builder cb = Canvas.newBuilder();
                try {
                    cb.mergeFrom(r.getPayload());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                cs.add(cb.build());
                return false;
            }
        });
        return cs;
    }

    public static List<Vote> getAliasVotes(Channel votes, String alias) throws IOException {
        List<Vote> vs = new ArrayList<>();
        votes.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                Record r = entry.getRecord();
                if (r.getCreator().equals(alias)) {
                    Vote.Builder vb = Vote.newBuilder();
                    try {
                        vb.mergeFrom(r.getPayload());
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                    vs.add(vb.build());
                }
                return false;
            }
        });
        return vs;
    }

    public static Colour getVotedColour(Channel votes, int x, int y, int z) throws IOException {
        Map<Colour, Integer> votedColours = new HashMap<>();
        votes.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                Record r = entry.getRecord();
                Vote.Builder vb = Vote.newBuilder();
                try {
                    vb.mergeFrom(r.getPayload());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                Vote v = vb.build();
                Location l = v.getLocation();
                if (l.getX() == x && l.getY() == y && l.getZ() == z) {
                    Colour c = v.getColour();
                    int count = 1;
                    if (votedColours.containsKey(c)) {
                        count = count + votedColours.get(c);
                    }
                    votedColours.put(c, count);
                }
                return false;
            }
        });
        Colour maxColour = null;
        int maxCount = 0;
        for (Colour c : votedColours.keySet()) {
            int count = votedColours.get(c);
            if (count > maxCount) {
                maxColour = c;
                maxCount = count;
            }
        }
        return maxColour;
    }

    public static Colour getLatestVote(Channel votes, int x, int y, int z) throws IOException {
        Colour[] votedColour = new Colour[1];
        votes.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                Record r = entry.getRecord();
                Vote.Builder vb = Vote.newBuilder();
                try {
                    vb.mergeFrom(r.getPayload());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                Vote v = vb.build();
                Location l = v.getLocation();
                if (l.getX() == x && l.getY() == y && l.getZ() == z) {
                    Colour c = v.getColour();
                    votedColour[0] = c;
                    return true;
                }
                return false;
            }
        });
        return votedColour[0];
    }

    public static List<Purchase> getAliasPurchases(Channel purchases, String alias) throws IOException {
        List<Purchase> ps = new ArrayList<>();
        purchases.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                Record r = entry.getRecord();
                if (r.getCreator().equals(alias)) {
                    Purchase.Builder pb = Purchase.newBuilder();
                    try {
                        pb.mergeFrom(r.getPayload());
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                    ps.add(pb.build());
                }
                return false;
            }
        });
        return ps;
    }

    public static Colour getPurchasedColour(Channel purchases, int x, int y, int z) throws IOException {
        Colour[] purchasedColour = new Colour[1];
        int[] maxPrice = new int[1];
        purchases.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                Record r = entry.getRecord();
                Purchase.Builder pb = Purchase.newBuilder();
                try {
                    pb.mergeFrom(r.getPayload());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                Purchase p = pb.build();
                Location l = p.getLocation();
                if (l.getX() == x && l.getY() == y && l.getZ() == z) {
                    int price = p.getPrice();
                    if (price > maxPrice[0]) {
                        purchasedColour[0] = p.getColour();
                        maxPrice[0] = price;
                    }
                }
                return false;
            }
        });
        return purchasedColour[0];
    }

    public static Colour getLatestPurchase(Channel purchases, int x, int y, int z) throws IOException {
        Colour[] purchasedColour = new Colour[1];
        purchases.iterate(new EntryCallback() {
            @Override
            public boolean onEntry(ByteString hash, Block block, BlockEntry entry) {
                Record r = entry.getRecord();
                Purchase.Builder pb = Purchase.newBuilder();
                try {
                    pb.mergeFrom(r.getPayload());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                Purchase p = pb.build();
                Location l = p.getLocation();
                if (l.getX() == x && l.getY() == y && l.getZ() == z) {
                    purchasedColour[0] = p.getColour();
                    return true;
                }
                return false;
            }
        });
        return purchasedColour[0];
    }
}
