import 'dart:math';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/services.dart';
import 'package:onnxruntime/onnxruntime.dart';

/// NetraProof On-Device AI Engine for Flutter (Android & iOS)
/// Zero cloud latency, works 100% offline.
class NetraProofOnDevice {
  OrtSession? _graderSession;
  OrtSession? _lesionSession;

  static const List<double> thresholds = [0.54, 1.62, 2.46, 3.36];
  static const List<String> gradeLabels = [
    "No DR (Normal)",
    "Mild NPDR",
    "Moderate NPDR (Referable)",
    "Severe NPDR (Referable)",
    "Proliferative DR (Referable)"
  ];

  Future<void> init() async {
    OrtEnv.instance.init();
    final sessionOptions = OrtSessionOptions()
      ..setIntraOpNumThreads(2)
      ..setSessionGraphOptimizationLevel(GraphOptimizationLevel.ortEnableAll);

    // Load from Flutter assets/
    final graderData = await rootBundle.load('assets/netraproof_grader_b5_int8.onnx');
    _graderSession = OrtSession.fromBuffer(graderData.buffer.asUint8List(), sessionOptions);

    final lesionData = await rootBundle.load('assets/netraproof_lesions_unet_int8.onnx');
    _lesionSession = OrtSession.fromBuffer(lesionData.buffer.asUint8List(), sessionOptions);
  }

  /// Screen 456x456 float input tensor on-device
  Future<Map<String, dynamic>> screen(Float32List chwNormalizedTensor) async {
    final inputOrt = OrtValueTensor.createTensorWithDataList(
      chwNormalizedTensor,
      [1, 3, 456, 456],
    );
    final runOptions = OrtRunOptions();
    final outputs = await _graderSession!.runAsync(runOptions, {'input': inputOrt});
    inputOrt.release();
    runOptions.release();

    final rawScore = (outputs![0]!.value as List)[0][0] as double;
    outputs.forEach((element) => element?.release());

    int grade = 0;
    while (grade < 4 && rawScore >= thresholds[grade]) {
      grade++;
    }

    return {
      'grade': grade,
      'gradeLabel': gradeLabels[grade],
      'isReferable': grade >= 2,
      'rawScore': rawScore,
    };
  }

  void dispose() {
    _graderSession?.release();
    _lesionSession?.release();
    OrtEnv.instance.release();
  }
}

