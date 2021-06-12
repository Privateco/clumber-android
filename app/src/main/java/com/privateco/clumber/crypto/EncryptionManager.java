/*
 * Copyright (c) 2021 Privateco and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.privateco.clumber.crypto;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HybridConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public class EncryptionManager {
    private KeysetHandle selfPrivateKeyHandle;  // not null
    private KeysetHandle selfPublicKeyHandle;  // not null
    private KeysetHandle peerPublicKeyHandle;  // nullable

    private static final EncryptionManager instance = new EncryptionManager();
    public static EncryptionManager getInstance() {
        return instance;
    }

    private EncryptionManager() {
        try {
            HybridConfig.register();
        } catch (GeneralSecurityException e) {
            // The app cannot continue if register failed; crash
            throw new RuntimeException(e);
        }
        reset();
    }

    public void reset() {
        try {
            selfPrivateKeyHandle = KeysetHandle.generateNew(KeyTemplates.get("ECIES_P256_COMPRESSED_HKDF_HMAC_SHA256_AES128_GCM"));
            selfPublicKeyHandle = selfPrivateKeyHandle.getPublicKeysetHandle();
        } catch (GeneralSecurityException e) {
            // The app cannot continue if key generation failed; crash
            throw new RuntimeException(e);
        }
        peerPublicKeyHandle = null;
    }

    public byte[] getSelfPublicKeyJson() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            CleartextKeysetHandle.write(selfPublicKeyHandle, JsonKeysetWriter.withOutputStream(outputStream));
        } catch (IOException e) {
            // The app cannot continue if public key is not extracted; crash
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }

    public boolean hasPeerPublicKey() {
        return peerPublicKeyHandle != null;
    }

    public void resetPeerPublicKeyHandle(byte[] peerPublicKeyBytes) throws GeneralSecurityException, IOException {
        peerPublicKeyHandle = KeysetHandle.readNoSecret(JsonKeysetReader.withBytes(peerPublicKeyBytes));
    }

    public byte[] encrypt(String input) throws GeneralSecurityException {
        if (peerPublicKeyHandle == null) {
            throw new IllegalStateException("Unable to encrypt when peerPublicKeyHandle is null");
        }
        HybridEncrypt encryptor = peerPublicKeyHandle.getPrimitive(HybridEncrypt.class);
        return encryptor.encrypt(input.getBytes(StandardCharsets.UTF_8), null);
    }

    public String decrypt(byte[] ciphertext) throws GeneralSecurityException {
        HybridDecrypt decryptor = selfPrivateKeyHandle.getPrimitive(HybridDecrypt.class);
        byte[] plaintext = decryptor.decrypt(ciphertext, null);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
