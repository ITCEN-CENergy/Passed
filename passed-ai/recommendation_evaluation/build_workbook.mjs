import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const baseDir = path.resolve("passed-ai/recommendation_evaluation");
const resultsDir = path.join(baseDir, "results");
const outputDir = path.resolve("outputs/recommendation-accuracy-20260823");
const previewDir = path.resolve(".codex-spreadsheet-work/recommendation-accuracy/previews");

const read = (file) => fs.readFile(file, "utf8");
const [rawCsv, aggregateCsv, profileCsv, detailCsv] = await Promise.all([
  read(path.join(baseDir, "data/recommendation_benchmark.csv")),
  read(path.join(resultsDir, "aggregate_metrics.csv")),
  read(path.join(resultsDir, "profile_metrics.csv")),
  read(path.join(resultsDir, "top10_recommendation_details.csv")),
]);

async function csvValues(csvText, sheetName) {
  const imported = await Workbook.fromCSV(csvText, { sheetName });
  return imported.worksheets.getItemAt(0).getUsedRange(true).values;
}

function coerceColumns(values, numericColumns = [], booleanColumns = []) {
  return values.map((row, rowIndex) => row.map((value, columnIndex) => {
    if (rowIndex === 0 || value === null || value === "") return value;
    if (numericColumns.includes(columnIndex)) return Number(value);
    if (booleanColumns.includes(columnIndex)) return String(value).toLowerCase() === "true";
    return value;
  }));
}

const [rawValues, aggregateValues, profileValues, detailValues] = await Promise.all([
  csvValues(rawCsv, "테스트 데이터"),
  csvValues(aggregateCsv, "집계 결과"),
  csvValues(profileCsv, "직무별 결과"),
  csvValues(detailCsv, "Top10 상세"),
]);

const typedRawValues = coerceColumns(rawValues, [6, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]);
const typedAggregateValues = coerceColumns(aggregateValues, [3, 4, 5]);
const typedProfileValues = coerceColumns(profileValues, [3, 4, 5, 6, 7, 8]);
const typedDetailValues = coerceColumns(detailValues, [5, 7, 8, 9], [11, 12]);

const workbook = Workbook.create();
const summary = workbook.worksheets.add("요약");
const criteria = workbook.worksheets.add("평가 기준");
const rawSheet = workbook.worksheets.add("테스트 데이터");
const aggregateSheet = workbook.worksheets.add("집계 결과");
const profileSheet = workbook.worksheets.add("직무별 결과");
const detailSheet = workbook.worksheets.add("Top10 상세");
rawSheet.getRange("A1:X301").values = typedRawValues;
aggregateSheet.getRange("A1:F9").values = typedAggregateValues;
profileSheet.getRange("A1:I21").values = typedProfileValues;
detailSheet.getRange("A1:M201").values = typedDetailValues;

const navy = "#1F2A44";
const blue = "#2563EB";
const blueLight = "#DBEAFE";
const gray = "#64748B";
const grayLight = "#E2E8F0";
const green = "#10B981";
const red = "#EF4444";
const white = "#FFFFFF";

summary.showGridLines = false;
summary.getRange("A1:N2").merge();
summary.getRange("A1").values = [["추천 정확도 추가 검증 — 개선 전후 비교"]];
summary.getRange("A1:N2").format = {
  fill: navy,
  font: { bold: true, color: white, size: 20 },
  verticalAlignment: "center",
  horizontalAlignment: "left",
};
summary.getRange("A3:N3").merge();
summary.getRange("A3").values = [["10개 직무 × 직무별 후보 30개 = 300 사용자-공고 쌍 · 상위 10개 추천을 동일 정책으로 평가"]];
summary.getRange("A3:N3").format = {
  fill: blueLight,
  font: { color: navy, size: 11 },
  verticalAlignment: "center",
};

summary.getRange("A5:D5").values = [["지표", "개선 전", "개선 후", "증감"]];
summary.getRange("A6:A9").values = [
  ["추천 정확도 (Precision@10)"],
  ["순위 품질 (NDCG@10)"],
  ["과대 추천률@10"],
  ["적합 공고 회수율 (Recall@10)"],
];
summary.getRange("B6:D9").formulas = [
  ["='집계 결과'!D2", "='집계 결과'!D6", "=C6-B6"],
  ["='집계 결과'!D3", "='집계 결과'!D7", "=C7-B7"],
  ["='집계 결과'!D4", "='집계 결과'!D8", "=C8-B8"],
  ["='집계 결과'!D5", "='집계 결과'!D9", "=C9-B9"],
];
summary.getRange("A5:D9").format.borders = {
  insideHorizontal: { style: "thin", color: grayLight },
  bottom: { style: "thin", color: grayLight },
};
summary.getRange("A5:D5").format = {
  fill: blue,
  font: { bold: true, color: white },
  horizontalAlignment: "center",
};
summary.getRange("A6:A9").format.font = { bold: true, color: navy };
summary.getRange("B6:D9").format.numberFormat = "0.0%";
summary.getRange("B6:D9").format.horizontalAlignment = "right";
summary.getRange("C6:C9").format = {
  fill: "#EFF6FF",
  font: { bold: true, color: blue },
  numberFormat: "0.0%",
};
summary.getRange("D6:D9").conditionalFormats.add("cellIs", {
  operator: "greaterThan",
  formula: 0,
  format: { font: { bold: true, color: green } },
});
summary.getRange("D6:D9").conditionalFormats.add("cellIs", {
  operator: "lessThan",
  formula: 0,
  format: { font: { bold: true, color: red } },
});

summary.getRange("A11:D11").merge();
summary.getRange("A11").values = [["핵심 결론"]];
summary.getRange("A11:D11").format = { fill: navy, font: { bold: true, color: white } };
summary.getRange("A12:C12").merge();
summary.getRange("A12").values = [["추천 정확도 상승폭"]];
summary.getRange("D12").formulas = [["=D6"]];
summary.getRange("A13:C13").merge();
summary.getRange("A13").values = [["과대 추천률 감소폭"]];
summary.getRange("D13").formulas = [["=B8-C8"]];
summary.getRange("A14:C14").merge();
summary.getRange("A14").values = [["평가 판정"]];
summary.getRange("D14").values = [["개선 확인"]];
summary.getRange("A12:D14").format.borders = {
  insideHorizontal: { style: "thin", color: grayLight },
  bottom: { style: "thin", color: grayLight },
};
summary.getRange("D12:D13").format = { numberFormat: "0.0%", font: { bold: true, color: green } };
summary.getRange("D14").format = { fill: "#D1FAE5", font: { bold: true, color: "#047857" }, horizontalAlignment: "center" };

summary.getRange("A16:D16").merge();
summary.getRange("A16").values = [["해석 시 주의"]];
summary.getRange("A16:D16").format = { fill: grayLight, font: { bold: true, color: navy } };
summary.getRange("A17:D19").merge();
summary.getRange("A17").values = [["이 결과는 알려진 의미 유사도 과대 매칭과 누락 스킬 복구 사례를 포함한 합성 회귀 데이터의 결과입니다. 개선 로직의 방향성 검증에는 사용할 수 있지만, 실제 지원·클릭·합격 데이터 기반 운영 정확도로 일반화하면 안 됩니다."]];
summary.getRange("A17:D19").format = { wrapText: true, verticalAlignment: "top", font: { color: gray } };

summary.getRange("F5:H5").values = [["지표", "개선 전", "개선 후"]];
summary.getRange("F6:H9").formulas = [
  ["=A6", "=B6", "=C6"],
  ["=A7", "=B7", "=C7"],
  ["=A8", "=B8", "=C8"],
  ["=A9", "=B9", "=C9"],
];
summary.getRange("F5:H9").format.font = { color: "#334155" };
summary.getRange("G6:H9").format.numberFormat = "0.0%";
const chart = summary.charts.add("bar", summary.getRange("F5:H9"));
chart.title = "개선 후 추천 품질은 높아지고 과대 추천은 감소";
chart.hasLegend = true;
chart.xAxis = { axisType: "textAxis", textStyle: { fontSize: 9 } };
chart.yAxis = { numberFormatCode: "0%", min: 0, max: 1 };
chart.setPosition("F11", "N27");

summary.getRange("A1:N27").format.font.name = "Aptos";
summary.getRange("A:A").format.columnWidth = 31;
summary.getRange("B:D").format.columnWidth = 14;
summary.getRange("E:E").format.columnWidth = 3;
summary.getRange("F:F").format.columnWidth = 31;
summary.getRange("G:H").format.columnWidth = 13;
summary.getRange("I:N").format.columnWidth = 11;
summary.getRange("1:3").format.rowHeight = 26;
summary.freezePanes.freezeRows(3);

criteria.showGridLines = false;
criteria.getRange("A1:D2").merge();
criteria.getRange("A1").values = [["추천 정확도 판정 기준 및 측정 지표"]];
criteria.getRange("A1:D2").format = { fill: navy, font: { bold: true, color: white, size: 18 }, verticalAlignment: "center" };
criteria.getRange("A4:C4").values = [["정답 등급", "판정", "독립 라벨 기준"]];
criteria.getRange("A5:C7").values = [
  [2, "적합", "동일 직무 + 필수 역량 직접 보유 또는 원문에서 완료된 직접 수행 근거 확인"],
  [1, "도전 가능", "동일 직무지만 요구 숙련도 또는 필수 역량 한 가지 부족"],
  [0, "부적합", "직무 불일치 또는 유사 용어만 있고 목표 역량의 직접 수행 근거 없음"],
];
criteria.getRange("A4:C4").format = { fill: blue, font: { bold: true, color: white } };
criteria.getRange("A5:C7").format.borders = { insideHorizontal: { style: "thin", color: grayLight } };
criteria.getRange("A5:A7").format.numberFormat = "0";
criteria.getRange("A5:B7").format.horizontalAlignment = "center";
criteria.getRange("C5:C7").format.wrapText = true;

criteria.getRange("A10:D10").values = [["지표", "정의", "용도", "판정 방향"]];
criteria.getRange("A11:D14").values = [
  ["Precision@10", "상위 10개 중 정답 등급 2의 비율", "추천 정확도 주 지표", "높을수록 좋음"],
  ["NDCG@10", "정답 등급 2·1·0과 추천 순서를 함께 반영", "순위 품질", "높을수록 좋음"],
  ["과대 추천률@10", "상위 10개 중 정답 등급 0의 비율", "오탐 안전성", "낮을수록 좋음"],
  ["Recall@10", "전체 정답 등급 2 중 상위 10개에 포함된 비율", "적합 공고 회수력", "높을수록 좋음"],
];
criteria.getRange("A10:D10").format = { fill: blue, font: { bold: true, color: white } };
criteria.getRange("A11:D14").format.borders = { insideHorizontal: { style: "thin", color: grayLight } };
criteria.getRange("B11:C14").format.wrapText = true;

criteria.getRange("A17:D17").values = [["공통 추천 점수 정책", "최대 점수", "적용 기준", "비고"]];
criteria.getRange("A18:D22").values = [
  ["필수 역량", 60, "보유/요구 개수 비율", "필수 보유율 50% 미만 후보 제외"],
  ["우대 역량", 20, "보유/요구 개수 비율", "개선 전후 동일"],
  ["관련 역량", 10, "보유/요구 개수 비율", "개선 전후 동일"],
  ["중요 스킬 보너스", 10, "중요 스킬 매칭 비율", "개선 전후 동일"],
  ["합계", 100, "", "현재 SKILL_MATCH v1 정책과 동일"],
];
criteria.getRange("A17:D17").format = { fill: blue, font: { bold: true, color: white } };
criteria.getRange("A18:D22").format.borders = { insideHorizontal: { style: "thin", color: grayLight } };
criteria.getRange("B18:B22").format.numberFormat = "0";
criteria.getRange("A1:D22").format.font.name = "Aptos";
criteria.getRange("A:A").format.columnWidth = 25;
criteria.getRange("B:B").format.columnWidth = 22;
criteria.getRange("C:C").format.columnWidth = 60;
criteria.getRange("D:D").format.columnWidth = 30;
criteria.getRange("5:7").format.rowHeight = 38;
criteria.freezePanes.freezeRows(2);

const importedSheets = [
  ["테스트 데이터", "A1:X301", "BenchmarkData"],
  ["집계 결과", "A1:F9", "AggregateMetrics"],
  ["직무별 결과", "A1:I21", "ProfileMetrics"],
  ["Top10 상세", "A1:M201", "Top10Details"],
];
for (const [sheetName, tableRange, tableName] of importedSheets) {
  const sheet = workbook.worksheets.getItem(sheetName);
  sheet.showGridLines = false;
  const table = sheet.tables.add(tableRange, true, tableName);
  table.style = "TableStyleMedium2";
  table.showBandedRows = true;
  table.showFilterButton = true;
  sheet.getRange(tableRange).format.font.name = "Aptos";
  sheet.getRange(tableRange).format.verticalAlignment = "top";
  sheet.freezePanes.freezeRows(1);
}

const raw = workbook.worksheets.getItem("테스트 데이터");
raw.freezePanes.freezeColumns(5);
raw.getRange("A:X").format.columnWidth = 14;
raw.getRange("C:C").format.columnWidth = 22;
raw.getRange("E:E").format.columnWidth = 24;
raw.getRange("I:I").format.columnWidth = 42;
raw.getRange("J:N").format.columnWidth = 34;
raw.getRange("J:N").format.wrapText = true;
raw.getRange("G:G").format.numberFormat = "0";
raw.getRange("H:H").conditionalFormats.add("containsText", { text: "적합", format: { fill: "#D1FAE5", font: { color: "#047857" } } });
raw.getRange("H:H").conditionalFormats.add("containsText", { text: "부적합", format: { fill: "#FEE2E2", font: { color: "#B91C1C" } } });

const aggregate = workbook.worksheets.getItem("집계 결과");
aggregate.getRange("A:F").format.columnWidth = 24;
aggregate.getRange("C:C").format.columnWidth = 34;
aggregate.getRange("D2:D9").format.numberFormat = "0.0%";

const profiles = workbook.worksheets.getItem("직무별 결과");
profiles.getRange("A:I").format.columnWidth = 18;
profiles.getRange("B:B").format.columnWidth = 24;
profiles.getRange("F:I").format.numberFormat = "0.0%";

const details = workbook.worksheets.getItem("Top10 상세");
details.getRange("A:M").format.columnWidth = 17;
details.getRange("B:B").format.columnWidth = 24;
details.getRange("D:D").format.columnWidth = 28;
details.getRange("J:J").format.numberFormat = "0.0";
details.getRange("K:K").format.numberFormat = "0.0%";

await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(previewDir, { recursive: true });

const inspection = await workbook.inspect({
  kind: "table",
  range: "요약!A1:H19",
  include: "values,formulas",
  tableMaxRows: 20,
  tableMaxCols: 8,
  maxChars: 8000,
});
console.log(inspection.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
  maxChars: 4000,
});
console.log(errors.ndjson);

for (const [sheetName, range] of [
  ["요약", "A1:N27"],
  ["평가 기준", "A1:D22"],
  ["테스트 데이터", "A1:X22"],
  ["집계 결과", "A1:F9"],
  ["직무별 결과", "A1:I21"],
  ["Top10 상세", "A1:M22"],
]) {
  const preview = await workbook.render({ sheetName, range, scale: 1, format: "png" });
  const safeName = sheetName.replaceAll("/", "-");
  await fs.writeFile(path.join(previewDir, `${safeName}.png`), new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
const outputPath = path.join(outputDir, "recommendation_accuracy_validation.xlsx");
await output.save(outputPath);
console.log(`saved ${outputPath}`);
