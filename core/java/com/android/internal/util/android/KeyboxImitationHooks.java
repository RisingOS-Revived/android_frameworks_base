/*
 * SPDX-FileCopyrightText: 2024 Paranoid Android
 * SPDX-FileCopyrightText: 2025 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.util.android;

import android.app.ActivityThread;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.security.keymint.Algorithm;
import android.hardware.security.keymint.KeyOrigin;
import android.hardware.security.keymint.KeyParameter;
import android.hardware.security.keymint.KeyParameterValue;
import android.hardware.security.keymint.KeyPurpose;
import android.hardware.security.keymint.SecurityLevel;
import android.hardware.security.keymint.Tag;
import android.os.Binder;
import android.os.IBinder;
import android.os.ServiceManager;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyStore2;
import android.security.KeyStoreException;
import android.security.KeyStoreSecurityLevel;
import android.system.keystore2.Authorization;
import android.system.keystore2.IKeystoreSecurityLevel;
import android.system.keystore2.IKeystoreService;
import android.system.keystore2.KeyDescriptor;
import android.system.keystore2.KeyEntryResponse;
import android.system.keystore2.KeyMetadata;
import android.util.Base64;
import android.util.Log;

import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1EncodableVector;
import com.android.internal.org.bouncycastle.asn1.ASN1Enumerated;
import com.android.internal.org.bouncycastle.asn1.ASN1Integer;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1OctetString;
import com.android.internal.org.bouncycastle.asn1.ASN1Primitive;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.ASN1TaggedObject;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.DERSequence;
import com.android.internal.org.bouncycastle.asn1.DERTaggedObject;
import com.android.internal.org.bouncycastle.asn1.x509.Extension;
import com.android.internal.org.bouncycastle.cert.X509CertificateHolder;
import com.android.internal.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.internal.org.bouncycastle.operator.ContentSigner;
import com.android.internal.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.android.internal.util.android.KeyboxChainGenerator;
import com.android.internal.util.android.KeyboxChainGenerator.KeyGenParameters;

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;

/**
 * @hide
 */
public class KeyboxImitationHooks {

    private static final String TAG = "KeyboxImitationHooks";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final ASN1ObjectIdentifier KEY_ATTESTATION_OID = new ASN1ObjectIdentifier(
            "1.3.6.1.4.1.11129.2.1.17");

    private static final Set<String> ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.google.android.gms"
    ));

    public static KeyEntryResponse onGetKeyEntry(KeyDescriptor descriptor) {
        if (!KeyProviderManager.isKeyboxAvailable()) {
            return null;
        }

        KeyEntryResponse spoofed = KeyboxUtils.retrieve(Binder.getCallingUid(), descriptor.alias);
        if (spoofed != null) {
            dlog("Key entry spoofed");
            return spoofed;
        }

        return null;
    }

    public static KeyEntryResponse fallbackKeyEntry(KeyEntryResponse response) {
        if (!KeyProviderManager.isKeyboxAvailable()) {
            dlog("Key attestation spoofing is disabled because no keybox is defined to spoof");
            return response;
        }

        if (response == null || response.metadata == null) return response;

        try {
            final Context ctx = ActivityThread.currentApplication();
            if (ctx == null) return response;

            final int uid = Binder.getCallingUid();
            final String[] packages = ctx.getPackageManager().getPackagesForUid(uid);
            if (packages == null) return response;

            if (Arrays.stream(packages)
                    .anyMatch(pkg -> ALLOWED_PACKAGES.contains(pkg))) {
                return response;
            }

            if (response.metadata.certificate == null) {
                Log.e(TAG, "Certificate is null, skipping modification");
                return response;
            }

            X509Certificate certificate = KeyChain.toCertificate(response.metadata.certificate);
            if (certificate.getExtensionValue(KEY_ATTESTATION_OID.getId()) == null) {
                Log.e(TAG, "Key attestation OID not found, skipping modification");
                return response;
            }

            String keyAlgorithm = certificate.getPublicKey().getAlgorithm();
            response.metadata.certificate = modifyLeafCertificate(certificate, keyAlgorithm);
            response.metadata.certificateChain = KeyboxUtils.getCertificateChainBytes(keyAlgorithm);
        } catch (Exception e) {
            Log.e(TAG, "Error in onGetKeyEntry", e);
        }

        return response;
    }

    private static byte[] modifyLeafCertificate(X509Certificate leafCertificate,
                                                String keyAlgorithm) throws Exception {
        X509CertificateHolder certificateHolder = new X509CertificateHolder(leafCertificate.getEncoded());
        Extension keyAttestationExtension = certificateHolder.getExtension(KEY_ATTESTATION_OID);
        ASN1Sequence keyAttestationSequence = ASN1Sequence.getInstance(
                keyAttestationExtension.getExtnValue().getOctets());
        ASN1Encodable[] keyAttestationEncodables = keyAttestationSequence.toArray();

        ASN1Sequence teeEnforcedSequence = (ASN1Sequence) keyAttestationEncodables[7];

        ASN1Encodable[] teeEnforcedEncodables = teeEnforcedSequence.toArray();
        ASN1EncodableVector teeEnforcedVector = new ASN1EncodableVector();
        for (ASN1Encodable enc : teeEnforcedEncodables) {
            teeEnforcedVector.add(enc);
        }

        Context context = ActivityThread.currentApplication();
        if (context == null) {
            Log.e(TAG, "Context is null in modifyLeafCertificate");
            return null;
        }
        SecureRandom secureRandom = new SecureRandom();

        String key = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.VBOOT_KEY);
        byte[] verifiedBootKey;
        if (key == null) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP);
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.VBOOT_KEY, encoded);
            verifiedBootKey = randomBytes;
        } else {
            verifiedBootKey = Base64.decode(key, Base64.NO_WRAP);
        }

        String hash = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.VBOOT_HASH);
        byte[] verifiedBootHash;
        if (hash == null) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP);
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.VBOOT_HASH, encoded);
            verifiedBootHash = randomBytes;
        } else {
            verifiedBootHash = Base64.decode(hash, Base64.NO_WRAP);
        }

        ASN1Encodable[] rootOfTrustEncodables = {
                new DEROctetString(verifiedBootKey),
                ASN1Boolean.TRUE,
                new ASN1Enumerated(0),
                new DEROctetString(verifiedBootHash)
        };
        ASN1Sequence newRootOfTrustSequence = new DERSequence(rootOfTrustEncodables);
        ASN1TaggedObject rootOfTrustTaggedObject = new DERTaggedObject(false, 704, newRootOfTrustSequence);

        ASN1EncodableVector filteredVector = new ASN1EncodableVector();
        for (ASN1Encodable enc : teeEnforcedEncodables) {
            if (enc instanceof ASN1TaggedObject) {
                ASN1TaggedObject tagged = (ASN1TaggedObject) enc;
                int tag = tagged.getTagNo();
                if (tag == 704 || tag == 705 || tag == 706 || tag == 718 || tag == 719) {
                    continue;
                }
            }
            filteredVector.add(enc);
        }

        filteredVector.add(rootOfTrustTaggedObject);
        filteredVector.add(new DERTaggedObject(true, 705,
                new ASN1Integer(KeyboxChainGenerator.getOsVersion())));
        filteredVector.add(new DERTaggedObject(true, 706,
                new ASN1Integer(KeyboxChainGenerator.getPatchLevel())));
        filteredVector.add(new DERTaggedObject(true, 718,
                new ASN1Integer(KeyboxChainGenerator.getPatchLevelLong())));
        filteredVector.add(new DERTaggedObject(true, 719,
                new ASN1Integer(KeyboxChainGenerator.getPatchLevelLong())));

        ASN1Sequence newTeeEnforcedSequence = new DERSequence(filteredVector);

        keyAttestationEncodables[7] = newTeeEnforcedSequence;

        ASN1Sequence newKeyAttestationSequence = new DERSequence(keyAttestationEncodables);
        ASN1OctetString newKeyAttestationOctetString = new DEROctetString(newKeyAttestationSequence);
        Extension newKeyAttestationExtension = new Extension(KEY_ATTESTATION_OID, false,
                newKeyAttestationOctetString);

        PrivateKey privateKey = KeyboxUtils.getPrivateKey(keyAlgorithm);
        X509CertificateHolder providerCertHolder = KeyboxUtils.getCertificateHolder(keyAlgorithm);

        X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
                providerCertHolder.getSubject(),
                certificateHolder.getSerialNumber(),
                certificateHolder.getNotBefore(),
                certificateHolder.getNotAfter(),
                certificateHolder.getSubject(),
                certificateHolder.getSubjectPublicKeyInfo()
        );

        ContentSigner contentSigner =
                new JcaContentSignerBuilder(leafCertificate.getSigAlgName()).build(privateKey);

        certificateBuilder.addExtension(newKeyAttestationExtension);

        for (ASN1ObjectIdentifier extensionOID :
                certificateHolder.getExtensions().getExtensionOIDs()) {
            if (KEY_ATTESTATION_OID.getId().equals(extensionOID.getId())) continue;
            certificateBuilder.addExtension(certificateHolder.getExtension(extensionOID));
        }

        return certificateBuilder.build(contentSigner).getEncoded();
    }

    public static KeyMetadata generateKey(IKeystoreSecurityLevel level, KeyDescriptor descriptor, Collection<KeyParameter> args) {
        if (!KeyProviderManager.isKeyboxAvailable()) {
            return null;
        }

        KeyGenParameters params = new KeyGenParameters(args.toArray(new KeyParameter[args.size()]));

        if (params.attestationChallenge == null) {
            return null;
        }

        if (params.purpose == null || !params.purpose.contains(KeyPurpose.SIGN)) {
            return null;
        }

        if (!params.noAuthRequired) {
            return null;
        }

        if (params.algorithm != Algorithm.EC && params.algorithm != Algorithm.RSA) {
            Log.w(TAG, "Unsupported algorithm: " + params.algorithm);
            return null;
        }

        try {
            final Context ctx = ActivityThread.currentApplication();
            if (ctx == null) return null;

            final int uid = Binder.getCallingUid();
            final String[] packages = ctx.getPackageManager().getPackagesForUid(uid);
            if (packages == null) return null;

            if (!Arrays.stream(packages)
                    .anyMatch(pkg -> ALLOWED_PACKAGES.contains(pkg))) {
                return null;
            }

            Certificate[] chain = KeyboxChainGenerator.generateCertChain(uid, descriptor, params);
            if (chain == null || chain.length == 0) {
                return null;
            }
            KeyEntryResponse response = buildResponse(level, chain, params, descriptor);
            if (response == null) {
                return null;
            }
            KeyboxUtils.append(uid, descriptor.alias, response);
            return response.metadata;
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate key", e);
            return null;
        }
    }

    private static KeyEntryResponse buildResponse(
            IKeystoreSecurityLevel level,
            Certificate[] chain,
            KeyGenParameters params,
            KeyDescriptor descriptor
    ) {
        try {
            KeyEntryResponse response = new KeyEntryResponse();
            KeyMetadata metadata = new KeyMetadata();
            int secLevel = resolveSecurityLevel(level);

            metadata.keySecurityLevel = secLevel;

            metadata.certificate = chain[0].getEncoded();
            var output = new ByteArrayOutputStream();
            for (int i = 1; i < chain.length; i++) {
                output.write(chain[i].getEncoded());
            }
            metadata.certificateChain = output.toByteArray();

            KeyDescriptor d = new KeyDescriptor();
            d.domain = descriptor.domain;
            d.nspace = descriptor.nspace;
            metadata.key = d;

            List<Authorization> authorizations = new ArrayList<>();
            Authorization a;

            for (Integer i : params.purpose) {
                a = new Authorization();
                a.keyParameter = new KeyParameter();
                a.keyParameter.tag = Tag.PURPOSE;
                a.keyParameter.value = KeyParameterValue.keyPurpose(i);
                a.securityLevel = secLevel;
                authorizations.add(a);
            }

            for (Integer i : params.digest) {
                a = new Authorization();
                a.keyParameter = new KeyParameter();
                a.keyParameter.tag = Tag.DIGEST;
                a.keyParameter.value = KeyParameterValue.digest(i);
                a.securityLevel = secLevel;
                authorizations.add(a);
            }

            if (Objects.equals(params.algorithm, Algorithm.RSA)) {
                for (Integer i : params.padding) {
                    a = new Authorization();
                    a.keyParameter = new KeyParameter();
                    a.keyParameter.tag = Tag.PADDING;
                    a.keyParameter.value = KeyParameterValue.paddingMode(i);
                    a.securityLevel = secLevel;
                    authorizations.add(a);
                }
            }

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.ALGORITHM;
            a.keyParameter.value = KeyParameterValue.algorithm(params.algorithm);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.KEY_SIZE;
            a.keyParameter.value = KeyParameterValue.integer(params.keySize);
            a.securityLevel = secLevel;
            authorizations.add(a);

            if (Objects.equals(params.algorithm, Algorithm.EC)) {
                a = new Authorization();
                a.keyParameter = new KeyParameter();
                a.keyParameter.tag = Tag.EC_CURVE;
                a.keyParameter.value = KeyParameterValue.ecCurve(params.ecCurve);
                a.securityLevel = secLevel;
                authorizations.add(a);
            }

            if (Objects.equals(params.algorithm, Algorithm.RSA)) {
                a = new Authorization();
                a.keyParameter = new KeyParameter();
                a.keyParameter.tag = Tag.RSA_PUBLIC_EXPONENT;
                a.keyParameter.value = KeyParameterValue.longInteger(params.rsaPublicExponent.longValue());
                a.securityLevel = secLevel;
                authorizations.add(a);
            }

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.NO_AUTH_REQUIRED;
            a.keyParameter.value = KeyParameterValue.boolValue(true);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.ORIGIN;
            a.keyParameter.value = KeyParameterValue.origin(params.origin);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.OS_VERSION;
            a.keyParameter.value = KeyParameterValue.integer(params.osVersion);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.OS_PATCHLEVEL;
            a.keyParameter.value = KeyParameterValue.integer(params.osPatchLevel);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.VENDOR_PATCHLEVEL;
            a.keyParameter.value = KeyParameterValue.integer(params.vendorPatchLevel);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.BOOT_PATCHLEVEL;
            a.keyParameter.value = KeyParameterValue.integer(params.bootPatchLevel);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.CREATION_DATETIME;
            a.keyParameter.value = KeyParameterValue.dateTime(params.creationDateTime);
            a.securityLevel = secLevel;
            authorizations.add(a);

            a = new Authorization();
            a.keyParameter = new KeyParameter();
            a.keyParameter.tag = Tag.USER_ID;
            a.keyParameter.value = KeyParameterValue.integer(params.userId);
            a.securityLevel = secLevel;
            authorizations.add(a);

            metadata.authorizations = authorizations.toArray(new Authorization[0]);
            metadata.modificationTimeMs = System.currentTimeMillis();
            response.metadata = metadata;
            response.iSecurityLevel = level;
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build key entry response", e);
            return null;
        }
    }

    private static final class SecLevelCache {
        static final IBinder SOFTWARE;
        static final IBinder TEE;
        static final IBinder STRONGBOX;

        static {
            IBinder sw = null, tee = null, sb = null;

            try {
                IBinder service = ServiceManager.getService(
                        "android.system.keystore2.IKeystoreService/default");
                IKeystoreService ks = IKeystoreService.Stub.asInterface(service);
                try { sw  = ks.getSecurityLevel(SecurityLevel.SOFTWARE).asBinder(); } catch (Exception ignored) {}
                try { tee = ks.getSecurityLevel(SecurityLevel.TRUSTED_ENVIRONMENT).asBinder(); } catch (Exception ignored) {}
                try { sb  = ks.getSecurityLevel(SecurityLevel.STRONGBOX).asBinder(); } catch (Exception ignored) {}
            } catch (Throwable t) {
                Log.w(TAG, "Failed to initialise Keystore cache", t);
            }

            SOFTWARE  = sw;
            TEE       = tee;
            STRONGBOX = sb;

            Log.i(TAG, "Keystore back-ends: " +
                    "SOFTWARE=" + (sw != null) +
                    ", TEE=" + (tee != null) +
                    ", STRONGBOX=" + (sb != null));
        }
    }

    private static int resolveSecurityLevel(IKeystoreSecurityLevel iSecLevel) {
        if (iSecLevel == null) {
            throw new IllegalArgumentException("iSecLevel is null");
        }
        final IBinder binder = iSecLevel.asBinder();

        if (binder == SecLevelCache.SOFTWARE) {
            return SecurityLevel.SOFTWARE;
        } else if (binder == SecLevelCache.TEE) {
            return SecurityLevel.TRUSTED_ENVIRONMENT;
        } else if (binder == SecLevelCache.STRONGBOX) {
            return SecurityLevel.STRONGBOX;
        } else {
            throw new IllegalStateException(
                    "Unknown IKeystoreSecurityLevel binder: " + binder);
        }
    }

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
