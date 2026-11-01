# NetraProof On-Device Mobile AI Deployment Guide

This directory contains production-ready, INT8-quantized on-device AI models for deploying NetraProof directly onto smartphones (Android & iOS).

## Why On-Device Mobile Inference?
Running on-device completely bypasses cloud server costs, network latency, and free-tier RAM limits (such as Render's 512MB ceiling which kills large segmentation models with `SIGKILL 137` / `502 Bad Gateway`). On-device execution works **100% offline** in remote clinics without cell signal.

---

## 1. Packaged Model Artifacts

| Model Name | Source Architecture | Size | Purpose | Peak RAM | Expected Latency |
|---|---|---|---|---|---|
| [`netraproof_grader_b5_int8.onnx`](file:///d:/AI%20Manthan/Project/retina-evidence/exports/mobile/netraproof_grader_b5_int8.onnx) | EfficientNet-B5 (Ordinal) | **27.8 MB** (was 108 MB) | Primary DR Grading (Grade 0–4 + Referral) | ~55 MB | ~200–400 ms (NNAPI) |
| [`netraproof_grader_b0_int8.onnx`](file:///d:/AI%20Manthan/Project/retina-evidence/exports/mobile/netraproof_grader_b0_int8.onnx) | EfficientNet-B0 (Ensemble Fold) | **4.1 MB** (was 15 MB) | Ultra-lightweight screening for 2GB budget phones | ~15 MB | ~80 ms |
| [`netraproof_lesions_unet_int8.onnx`](file:///d:/AI%20Manthan/Project/retina-evidence/exports/mobile/netraproof_lesions_unet_int8.onnx) | U-Net SE-ResNeXt50_32x4d | **33.3 MB** (was 132 MB) | Retinal Lesion Segmentation (EX, HE, CTW, MA) | ~65 MB | ~1.2 s |

---

## 2. Quick Setup: Android Native (Kotlin)

### Step 1: Add Dependencies
In your Android app's `app/build.gradle.kts`:
```kotlin
dependencies {
    // ONNX Runtime Mobile with NNAPI Hardware Acceleration
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
}
```

### Step 2: Copy Assets
Copy the `.onnx` files from this directory into your app's `app/src/main/assets/` directory:
- `netraproof_grader_b5_int8.onnx`
- `netraproof_lesions_unet_int8.onnx`

### Step 3: Run Inference
Drop [`NetraProofOnDevice.kt`](file:///d:/AI%20Manthan/Project/retina-evidence/exports/mobile/NetraProofOnDevice.kt) into your project:
```kotlin
val netraProof = NetraProofOnDevice(context)

// 1. Primary Screening
val result = netraProof.screen(capturedFundusBitmap)
Log.i("NetraProof", "Grade: ${result.gradeLabel}, Referable: ${result.isReferable}")

// 2. Lesion Overlay Generation
val lesionResult = netraProof.segmentLesions(capturedFundusBitmap)
imageViewOverlay.setImageBitmap(lesionResult.overlayBitmap)
```

---

## 3. Quick Setup: Flutter

### Step 1: Add Dependency
In your `pubspec.yaml`:
```yaml
dependencies:
  onnxruntime: ^1.1.2
```

### Step 2: Add Assets
```yaml
flutter:
  assets:
    - assets/netraproof_grader_b5_int8.onnx
    - assets/netraproof_lesions_unet_int8.onnx
```

### Step 3: Run Inference
Drop [`netraproof_on_device.dart`](file:///d:/AI%20Manthan/Project/retina-evidence/exports/mobile/netraproof_on_device.dart) into `lib/services/`.

---

## 4. Verification Checksum & Parameters
See [`models_metadata.json`](file:///d:/AI%20Manthan/Project/retina-evidence/exports/mobile/models_metadata.json) for input normalizations, exact float32 ImageNet tensors, and ordinal thresholds (`[0.54, 1.62, 2.46, 3.36]`).

