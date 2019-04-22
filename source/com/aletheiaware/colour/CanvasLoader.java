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

package com.aletheiaware.colour;

import com.aletheiaware.bc.BC.Channel;
import com.aletheiaware.bc.BC.Channel.EntryCallback;
import com.aletheiaware.bc.BCProto.Block;
import com.aletheiaware.bc.BCProto.BlockEntry;
import com.aletheiaware.bc.BCProto.Record;
import com.aletheiaware.bc.utils.BCUtils;
import com.aletheiaware.colour.ColourProto.Canvas;
import com.aletheiaware.colour.ColourProto.Colour;
import com.aletheiaware.colour.ColourProto.Location;
import com.aletheiaware.colour.ColourProto.Purchase;
import com.aletheiaware.colour.ColourProto.Vote;
import com.aletheiaware.colour.utils.ColourUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public abstract class CanvasLoader {

    private final File cache;
    private final InetAddress host;
    private final byte[] canvasRecordHash;
    private final String canvasId;
    private Canvas canvas = null;

    public CanvasLoader(File cache, InetAddress host, byte[] canvasRecordHash) {
        this.cache = cache;
        this.host = host;
        this.canvasRecordHash = canvasRecordHash;
        canvasId = new String(BCUtils.encodeBase64URL(canvasRecordHash));
    }

    public String getCanvasId() {
        return canvasId;
    }

    public byte[] getCanvasHash() {
        return canvasRecordHash;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public void loadCanvas() {
        Channel canvases = ColourUtils.getCanvasesChannel(cache, host);
        try {
            canvas = ColourUtils.getCanvas(canvases, canvasRecordHash);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (canvas != null) {
            onCanvasLoaded(canvas);
        }
    }

    public void loadLocationColour() throws IOException {
        if (canvas == null) {
            return;
        }
        String canvasId = new String(BCUtils.encodeBase64URL(canvasRecordHash));
        Channel votes = ColourUtils.getVotesChannel(canvasId, cache, host);
        Channel purchases = ColourUtils.getPurchasesChannel(canvasId, cache, host);
        switch (canvas.getMode()) {
            case FREE_FOR_ALL:
                final Set<Location> ps = new HashSet<>();
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
                        System.out.println(ColourUtils.TAG + "Purchase: " + p);
                        if (!ps.contains(p.getLocation())) {
                            onLocationColour(p.getLocation(), p.getColour());
                            ps.add(p.getLocation());
                        }
                        return true;
                    }
                });
                break;
            case COLOUR_MARKET:
                break;
            case RADICAL_COLOUR_MARKET:
                break;
            case LOCATION_MARKET:
                break;
            case RADICAL_LOCATION_MARKET:
                break;
            case ONE_ALIAS_ONE_VOTE:
                final Set<String> vs = new HashSet<>();
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
                        System.out.println(ColourUtils.TAG + "Vote: " + v);
                        if (!vs.contains(r.getCreator())) {
                            onLocationColour(v.getLocation(), v.getColour());
                            vs.add(r.getCreator());
                        }
                        return true;
                    }
                });
                break;
            case QUADRATIC_VOTE:
                break;
            case UNRECOGNIZED:
                // fallthrough
            case UNKNOWN_MODE:
                // fallthrough
            default:
                break;
        }
    }

    public abstract void onCanvasLoaded(Canvas canvas);

    public abstract void onLocationColour(Location location, Colour colour);
}
