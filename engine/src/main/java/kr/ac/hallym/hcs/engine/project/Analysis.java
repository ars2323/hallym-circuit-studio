/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Entry;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.analyze.model.OutputExpressions;
import com.cburch.logisim.analyze.model.TruthTable;
import com.cburch.logisim.circuit.Analyze;
import com.cburch.logisim.circuit.AnalyzeException;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.Doc;

/**
 * Project › Analyze Circuit, Get Circuit Statistics(원조 메뉴, N-21, D-162): 원조의 계산 클래스를 그대로 부르고 창만
 * 뺐다. 모두 읽기만 한다(회로·파일을 바꾸지 않는다). Analyze Circuit은 원조 {@code ProjectCircuitActions.doAnalyze}의
 * 차례(핀 이름 → 여러 비트 핀·개수 검사 → 식 → 안 되면 시뮬레이션으로 진리표), 원조 {@link AnalyzerModel}이 식에서
 * 진리표와 최소식을 만든다. Get Circuit Statistics는 원조 FileStatistics의 셈(StatisticsDialog의 표, {@link Statistics}: 도구를 새로 불러오지 않는다).
 */
public final class Analysis {
    private Analysis() {
    }

    /**
     * model.analyze = {circuit, inputs:[이름], outputs:[이름], problem?, source:"expression"|"table"|null,
     * expressionFailure?, table:{rows:[[입력…, 출력…]]}, expressions:[{output, expression, sop, pos}]}.
     * problem(원조가 오류 창으로 멈추던 것): "multibitInput", "multibitOutput", "tooManyInputs", "tooManyOutputs"(max와
     * 함께), "noInputs", "noOutputs"(원조는 Inputs·Outputs 탭에 멈춘다). 칸은 원조 Entry: "0", "1", "x"(상관없음),
     * "E"(출력 충돌), "!!"(진동).
     */
    public static JsonObject analyze(Doc d, Circuit circuit) {
        JsonObject o = new JsonObject();
        o.addProperty("circuit", circuit.getName());
        o.addProperty("maxInputs", AnalyzerModel.MAX_INPUTS);
        o.addProperty("maxOutputs", AnalyzerModel.MAX_OUTPUTS);
        SortedMap<Instance, String> pinNames = Analyze.getPinLabels(circuit);
        List<String> inputNames = new ArrayList<>();
        List<String> outputNames = new ArrayList<>();
        String problem = null;
        String problemPin = null;
        for (Map.Entry<Instance, String> e : pinNames.entrySet()) {
            Instance pin = e.getKey();
            boolean input = Pin.FACTORY.isInputPin(pin);
            (input ? inputNames : outputNames).add(e.getValue());
            if (problem == null && pin.getAttributeValue(StdAttr.WIDTH).getWidth() > 1) {
                problem = input ? "multibitInput" : "multibitOutput";
                problemPin = e.getValue();
            }
        }
        o.add("inputs", strings(inputNames));
        o.add("outputs", strings(outputNames));
        if (problem == null && inputNames.size() > AnalyzerModel.MAX_INPUTS) {
            problem = "tooManyInputs";
        }
        if (problem == null && outputNames.size() > AnalyzerModel.MAX_OUTPUTS) {
            problem = "tooManyOutputs";
        }
        if (problem == null && inputNames.isEmpty()) {
            problem = "noInputs";
        }
        if (problem == null && outputNames.isEmpty()) {
            problem = "noOutputs";
        }
        if (problem != null) {
            o.addProperty("problem", problem);
            if (problemPin != null) {
                o.addProperty("pin", problemPin);
            }
            o.add("source", null);
            return o;
        }
        AnalyzerModel model = new AnalyzerModel();
        model.setCurrentCircuit(d.project(), circuit);
        model.setVariables(inputNames, outputNames);
        String source;
        try {
            Analyze.computeExpression(model, circuit, pinNames);
            source = "expression";
        } catch (AnalyzeException ex) {
            // 원조: 알림 창("analyzeNoExpressionTitle") 뒤 시뮬레이션으로 진리표를 만든다
            o.addProperty("expressionFailure", ex.getMessage());
            Analyze.computeTable(model, d.project(), circuit, pinNames);
            source = "table";
        }
        o.addProperty("source", source);
        o.add("table", table(model.getTruthTable()));
        OutputExpressions ex = model.getOutputExpressions();
        JsonArray exprs = new JsonArray();
        for (String out : outputNames) {
            JsonObject x = new JsonObject();
            x.addProperty("output", out);
            Expression e = ex.getExpression(out);
            x.addProperty("expression", e == null ? null : e.toString());
            int format = ex.getMinimizedFormat(out);
            ex.setMinimizedFormat(out, AnalyzerModel.FORMAT_SUM_OF_PRODUCTS);
            x.addProperty("sop", text(ex.getMinimalExpression(out)));
            ex.setMinimizedFormat(out, AnalyzerModel.FORMAT_PRODUCT_OF_SUMS);
            x.addProperty("pos", text(ex.getMinimalExpression(out)));
            ex.setMinimizedFormat(out, format);
            exprs.add(x);
        }
        o.add("expressions", exprs);
        return o;
    }

    private static String text(Expression e) {
        return e == null ? null : e.toString();
    }

    private static JsonObject table(TruthTable t) {
        JsonObject o = new JsonObject();
        JsonArray rows = new JsonArray();
        int ins = t.getInputColumnCount();
        int outs = t.getOutputColumnCount();
        for (int r = 0; r < t.getRowCount(); r++) {
            StringBuilder in = new StringBuilder();
            for (int c = 0; c < ins; c++) {
                in.append(TruthTable.isInputSet(r, c, ins) ? '1' : '0');
            }
            JsonArray row = new JsonArray();
            row.add(in.toString());
            for (int c = 0; c < outs; c++) {
                row.add(cell(t.getOutputEntry(r, c)));
            }
            rows.add(row);
        }
        o.add("rows", rows);
        return o;
    }

    /** 원조 Entry의 글자. 오류 둘은 원조가 모두 "!!"로 보이지만 어떤 오류인지 가른다. */
    static String cell(Entry e) {
        if (e == Entry.BUS_ERROR) {
            return "E";
        }
        if (e == Entry.OSCILLATE_ERROR) {
            return "!!";
        }
        return e.getDescription();
    }

    /**
     * model.statistics = {circuit, rows:[{component, library, simple, unique, recursive}], without:{…}, with:{…}}:
     * 원조 StatisticsDialog의 표({@link Statistics}). library는 라이브러리 보이는 이름("-": 없음, 이 파일의 회로는 파일 이름).
     */
    public static JsonObject statistics(Doc d, Circuit circuit) {
        Statistics stats = Statistics.compute(d.file(), circuit);
        JsonObject o = new JsonObject();
        o.addProperty("circuit", circuit.getName());
        JsonArray rows = new JsonArray();
        for (Statistics.Count c : stats.counts) {
            JsonObject r = count(c);
            r.addProperty("component", c.factory.getDisplayName());
            r.addProperty("library", c.library == null ? "-" : c.library.getDisplayName());
            rows.add(r);
        }
        o.add("rows", rows);
        o.add("without", count(stats.without));
        o.add("with", count(stats.with));
        return o;
    }

    private static JsonObject count(Statistics.Count c) {
        JsonObject r = new JsonObject();
        r.addProperty("simple", c.simple);
        r.addProperty("unique", c.unique);
        r.addProperty("recursive", c.recursive);
        return r;
    }

    private static JsonArray strings(List<String> xs) {
        JsonArray a = new JsonArray();
        xs.forEach(a::add);
        return a;
    }
}
