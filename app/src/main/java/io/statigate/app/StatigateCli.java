/*
 * Copyright 2026 The Statigate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.statigate.app;

import io.statigate.advisory.LlmClient;
import io.statigate.advisory.jlama.JlamaLlmClient;
import io.statigate.extraction.OnnxRuntimeProbe;
import io.statigate.nlp.ModelLocator;
import io.statigate.nlp.NlpRuntime;
import io.statigate.pipeline.AnalysisPipeline;
import io.statigate.pipeline.PipelineResult;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line entry point for the offline legal document analysis pipeline.
 *
 * <pre>
 * statigate &lt;file&gt; [options]
 *   --json                 emit JSON instead of the text report
 *   --models DIR           model asset directory (default: ./models or $STATIGATE_MODELS)
 *   --no-model             skip InLegalBERT even if present (keyword/BM25 only)
 *   --llm jlama            rephrase advice with a local JLama model (needs --llm-model)
 *   --llm-model DIR        local JLama model directory
 *   --threads N            ONNX intra-op threads (default: half the CPUs)
 *   --check-onnx           verify ONNX Runtime native libraries and exit
 * </pre>
 */
public final class StatigateCli {

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (UsageException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            printUsage(System.err);
            System.exit(2);
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) throws Exception {
        Path file = null;
        Path modelsDir = null;
        Path llmModelDir = null;
        boolean json = false;
        boolean noModel = false;
        boolean checkOnnx = false;
        String llm = null;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> {
                    printUsage(System.out);
                    return 0;
                }
                case "--json" -> json = true;
                case "--no-model" -> noModel = true;
                case "--check-onnx" -> checkOnnx = true;
                case "--models" -> modelsDir = Path.of(requireArg(args, ++i, "--models"));
                case "--llm" -> llm = requireArg(args, ++i, "--llm");
                case "--llm-model" -> llmModelDir = Path.of(requireArg(args, ++i, "--llm-model"));
                case "--threads" -> threads = parsePositiveInt(requireArg(args, ++i, "--threads"));
                default -> {
                    if (args[i].startsWith("-")) {
                        throw new UsageException("unknown option: " + args[i]);
                    }
                    if (file != null) {
                        throw new UsageException("more than one input file given");
                    }
                    file = Path.of(args[i]);
                }
            }
        }

        if (checkOnnx) {
            var r = new OnnxRuntimeProbe().probe();
            System.out.printf("ONNX Runtime: %s | %s | providers=%s | status=%s%n",
                    r.ortVersion(), r.nativeArchLabel(), r.providers(),
                    r.nativeLoaded() ? "OK" : "FAILED");
            if (file == null) {
                return r.nativeLoaded() ? 0 : 1;
            }
        }
        if (file == null) {
            throw new UsageException("no input file given");
        }
        if (!Files.isRegularFile(file)) {
            throw new UsageException("not a readable file: " + file);
        }

        ModelLocator locator = noModel
                ? new ModelLocator(Path.of("__statigate_no_model__"))
                : (modelsDir != null ? new ModelLocator(modelsDir) : new ModelLocator());

        try (NlpRuntime runtime = new NlpRuntime(locator, 256, threads);
                LlmClient llmClient = buildLlm(llm, llmModelDir)) {

            AnalysisPipeline pipeline = llmClient != null
                    ? AnalysisPipeline.withLlm(runtime, llmClient)
                    : AnalysisPipeline.create(runtime);

            PipelineResult result = pipeline.analyze(file);
            if (json) {
                JsonReportWriter.write(result, System.out);
            } else {
                ReportPrinter.print(result, System.out);
            }
        }
        return 0;
    }

    private static LlmClient buildLlm(String llm, Path modelDir) {
        if (llm == null) {
            return null;
        }
        if (!"jlama".equalsIgnoreCase(llm)) {
            throw new UsageException("unknown --llm backend: " + llm + " (supported: jlama)");
        }
        if (modelDir == null) {
            throw new UsageException("--llm jlama requires --llm-model DIR");
        }
        return new JlamaLlmClient(modelDir);
    }

    private static String requireArg(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new UsageException(opt + " requires a value");
        }
        return args[i];
    }

    private static int parsePositiveInt(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            if (v <= 0) {
                throw new UsageException("expected a positive number: " + s);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new UsageException("expected a number: " + s);
        }
    }

    private static void printUsage(java.io.PrintStream out) {
        out.println("""
                Usage: statigate <file> [options]

                  --json              emit JSON instead of the text report
                  --models DIR        model asset directory (default ./models or $STATIGATE_MODELS)
                  --no-model          keyword + BM25 only, skip InLegalBERT
                  --llm jlama         rephrase advice with a local JLama model
                  --llm-model DIR     local JLama model directory
                  --threads N         ONNX intra-op threads
                  --check-onnx        verify ONNX Runtime native libraries
                  -h, --help          this help

                Output is advisory information only, not legal advice.""");
    }

    private static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    private StatigateCli() {
    }
}
