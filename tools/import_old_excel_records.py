from __future__ import annotations

import argparse
import datetime as dt
import getpass
import hashlib
import json
import mimetypes
import re
import sys
import tempfile
import uuid
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any
from urllib import error, parse, request

import openpyxl


DEFAULT_INPUT_DIR = Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)")
DEFAULT_BASE_URL = "https://www.gashbxy.com"
DEFAULT_REPORT = Path("outputs/old_excel_import_preview.json")

VALID_CUSTOMER_TYPES = {"居民用户", "商业用户", "餐饮用户", "工业用户", "流动摊贩"}
VALID_RESULTS = {"隐患已清零", "隐患未清零", "隐患已整改"}

HAZARD_NAME_TO_CODE = {
    "未安装可燃气体报警器（重大隐患）": "alarm_missing",
    "可燃气体报警器故障需更换": "alarm_fault",
    "可燃气体报警器检测气体类型错误": "alarm_wrong_gas",
    "如报警器未插电或安装位置不对，现场整改，不做统计": "alarm_site_fix",
    "有三通（重大隐患）": "hose_tee",
    "非国标软管（重大隐患）": "hose_non_standard",
    "软管穿墙或暗埋": "hose_hidden",
    "软管未用管箍固定（重大隐患）": "hose_no_clamp",
    "软管长度超过2米": "hose_too_long",
    "在地下室、半地下室等通风不畅的场所使用瓶装液化气（重大隐患）": "environment_bad",
    "使用可调式减压阀": "valve_adjustable",
    "未正确填写并张贴安全用气指南": "guide_wrong",
}

GAS_SOURCE_ISSUES = {
    "钢瓶扫码无信息",
    "钢瓶无二维码",
    "钢瓶超期或报废",
    "钢瓶充装日期与客户实际使用周期不符",
    "钢瓶为非鑫远公司气瓶",
}

CATEGORY_TO_LEGACY_PHOTO_CODE = {
    "可燃气体报警器": "legacy_alarm_photo",
    "软管": "legacy_hose_photo",
    "用气环境": "legacy_environment_photo",
    "减压阀": "legacy_valve_photo",
    "安全用气指南": "legacy_guide_photo",
}


def cell_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, dt.datetime):
        return value.date().isoformat()
    if isinstance(value, dt.date):
        return value.isoformat()
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value).strip()


def split_items(value: str) -> list[str]:
    if not value:
        return []
    parts = re.split(r"[\n\r；;、]+", value)
    return [part.strip() for part in parts if part.strip()]


def legacy_client_uuid(file: Path, row: int, row_values: dict[str, str]) -> str:
    raw = "|".join(
        [
            file.name,
            str(row),
            row_values.get("sequence", ""),
            row_values.get("customerName", ""),
            row_values.get("customerPhone", ""),
            row_values.get("inspectionDate", ""),
        ]
    )
    return "legacy-xlsx-" + hashlib.sha1(raw.encode("utf-8")).hexdigest()[:32]


def image_position(image: object) -> tuple[int, int] | None:
    try:
        anchor = image.anchor
        return anchor._from.row + 1, anchor._from.col + 1
    except Exception:
        return None


def image_bytes(image: object) -> tuple[bytes, str]:
    data = image._data()
    suffix = Path(getattr(image, "path", "") or "").suffix.lower() or ".jpg"
    if suffix == ".jpeg":
        suffix = ".jpg"
    return data, suffix


def parse_layout(sheet: Any) -> dict[str, Any]:
    hazard_issue_cols: dict[str, int] = {}
    hazard_photo_cols: dict[str, list[int]] = defaultdict(list)
    cylinder_qr_cols: list[int] = []
    cylinder_cols: list[int] = []
    rectification_photo_cols: list[int] = []
    rectification_date_col = 0
    rectification_note_col = 0
    rectification_person_col = 0

    current_category = ""
    for col in range(1, sheet.max_column + 1):
        top = cell_text(sheet.cell(2, col).value)
        sub = cell_text(sheet.cell(3, col).value)
        if sub in CATEGORY_TO_LEGACY_PHOTO_CODE:
            current_category = sub
            hazard_issue_cols[sub] = col
            continue
        if sub == "照片" and current_category:
            hazard_photo_cols[current_category].append(col)
            continue
        if top.startswith("钢瓶二维码照片"):
            cylinder_qr_cols.append(col)
            continue
        if top.startswith("钢瓶照片"):
            cylinder_cols.append(col)
            continue
        if top.startswith("整改照片"):
            rectification_photo_cols.append(col)
            continue
        if top == "整改日期":
            rectification_date_col = col
            continue
        if top == "整改情况":
            rectification_note_col = col
            continue
        if top == "整改人":
            rectification_person_col = col

    return {
        "hazardIssueCols": hazard_issue_cols,
        "hazardPhotoCols": dict(hazard_photo_cols),
        "cylinderQrCols": cylinder_qr_cols,
        "cylinderCols": cylinder_cols,
        "rectificationPhotoCols": rectification_photo_cols,
        "rectificationDateCol": rectification_date_col,
        "rectificationNoteCol": rectification_note_col,
        "rectificationPersonCol": rectification_person_col,
    }


def classify_photo(col: int, layout: dict[str, Any], hazard_codes_by_category: dict[str, list[str]]) -> tuple[str, str | None]:
    if col == 3:
        return "storefront", None
    for category, cols in layout["hazardPhotoCols"].items():
        if col in cols:
            codes = hazard_codes_by_category.get(category, [])
            return "hazard", codes[0] if codes else CATEGORY_TO_LEGACY_PHOTO_CODE[category]
    if col in layout["cylinderQrCols"]:
        return "cylinder_qr", None
    if col in layout["cylinderCols"]:
        return "cylinder", None
    if col in layout["rectificationPhotoCols"]:
        return "rectification", None
    return "ignored", None


def build_record(file: Path, sheet: Any, row: int, layout: dict[str, Any], images_by_row: dict[int, list[object]]) -> dict[str, Any]:
    values = {
        "sequence": cell_text(sheet.cell(row, 1).value),
        "customerName": cell_text(sheet.cell(row, 2).value),
        "customerPhone": cell_text(sheet.cell(row, 4).value),
        "customerType": cell_text(sheet.cell(row, 5).value),
        "customerAddress": cell_text(sheet.cell(row, 6).value),
        "inspectorName": cell_text(sheet.cell(row, 7).value),
        "inspectionDate": cell_text(sheet.cell(row, 8).value),
        "checkResult": cell_text(sheet.cell(row, 9).value),
        "deliveryPerson": cell_text(sheet.cell(row, 20).value) or "旧版导入未填写",
        "remark": cell_text(sheet.cell(row, 21).value),
    }
    if layout["rectificationDateCol"]:
        values["rectificationDate"] = cell_text(sheet.cell(row, layout["rectificationDateCol"]).value)
    if layout["rectificationNoteCol"]:
        values["rectificationNote"] = cell_text(sheet.cell(row, layout["rectificationNoteCol"]).value)
    if layout["rectificationPersonCol"]:
        values["rectificationPerson"] = cell_text(sheet.cell(row, layout["rectificationPersonCol"]).value)

    hazard_items: list[str] = []
    hazard_codes_by_category: dict[str, list[str]] = defaultdict(list)
    unmapped_hazards: list[str] = []
    for category, col in layout["hazardIssueCols"].items():
        for issue in split_items(cell_text(sheet.cell(row, col).value)):
            code = HAZARD_NAME_TO_CODE.get(issue)
            if code:
                hazard_items.append(code)
                hazard_codes_by_category[category].append(code)
            else:
                unmapped_hazards.append(f"{category}:{issue}")

    gas_source_issues: list[str] = []
    for issue in split_items(cell_text(sheet.cell(row, 22).value)):
        if issue in GAS_SOURCE_ISSUES:
            gas_source_issues.append(issue)
        else:
            gas_source_issues.append(issue)

    photos: dict[str, Any] = {
        "storefront": [],
        "cylinder_qr": [],
        "cylinder": [],
        "rectification": [],
        "hazard": defaultdict(list),
    }
    pending_images: list[dict[str, Any]] = []
    ignored_image_cols: list[int] = []
    for image in images_by_row.get(row, []):
        position = image_position(image)
        if not position:
            continue
        _, col = position
        photo_type, hazard_code = classify_photo(col, layout, hazard_codes_by_category)
        if photo_type == "ignored":
            ignored_image_cols.append(col)
            continue
        pending_images.append({"image": image, "type": photo_type, "hazardCode": hazard_code, "col": col})

    payload = {
        "clientUuid": legacy_client_uuid(file, row, values),
        "customerName": values["customerName"],
        "customerPhone": values["customerPhone"] or "旧版导入未填写",
        "customerType": values["customerType"] if values["customerType"] in VALID_CUSTOMER_TYPES else "餐饮用户",
        "customerAddress": values["customerAddress"],
        "inspectorName": values["inspectorName"],
        "deliveryPerson": values["deliveryPerson"],
        "inspectionDate": values["inspectionDate"],
        "checkResult": values["checkResult"] if values["checkResult"] in VALID_RESULTS else "隐患已清零",
        "hazardItems": hazard_items,
        "gasSourceIssues": gas_source_issues,
        "remark": values["remark"],
        "rectificationDate": values.get("rectificationDate", ""),
        "rectificationNote": values.get("rectificationNote", ""),
        "rectificationPerson": values.get("rectificationPerson", ""),
        "location": {"address": values["customerAddress"]},
        "photos": photos,
    }

    if payload["checkResult"] == "隐患未清零" and not hazard_items and not gas_source_issues:
        payload["hazardItems"] = ["legacy_unclassified_hazard"]
        unmapped_hazards.append("旧表未填写隐患项目，导入时使用 legacy_unclassified_hazard 占位")

    return {
        "file": file,
        "row": row,
        "values": values,
        "payload": payload,
        "pendingImages": pending_images,
        "unmappedHazards": unmapped_hazards,
        "ignoredImageCols": ignored_image_cols,
    }


def iter_records(files: list[Path]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for file in files:
        workbook = openpyxl.load_workbook(file, data_only=True)
        sheet = workbook.active
        layout = parse_layout(sheet)
        images_by_row: dict[int, list[object]] = defaultdict(list)
        for image in getattr(sheet, "_images", []):
            position = image_position(image)
            if position:
                images_by_row[position[0]].append(image)
        for row in range(4, sheet.max_row + 1):
            if not (sheet.cell(row, 1).value or sheet.cell(row, 2).value):
                continue
            records.append(build_record(file, sheet, row, layout, images_by_row))
    return records


def api_json(base_url: str, path: str, token: str | None = None, payload: dict[str, Any] | None = None, method: str = "POST") -> dict[str, Any]:
    url = base_url.rstrip("/") + path
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
    except error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} failed: HTTP {exc.code} {body}") from exc
    result = json.loads(raw or "{}")
    if result.get("ok") is False:
        raise RuntimeError(f"{method} {path} failed: {result}")
    return result.get("data", result)


def upload_file(base_url: str, token: str, filename: str, data: bytes) -> str:
    boundary = "----xinyuan-import-" + uuid.uuid4().hex
    content_type = mimetypes.guess_type(filename)[0] or "application/octet-stream"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode("utf-8") + data + f"\r\n--{boundary}--\r\n".encode("utf-8")
    req = request.Request(
        base_url.rstrip("/") + "/api/files/upload",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read().decode("utf-8"))
    except error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"upload failed: HTTP {exc.code} {body_text}") from exc
    if result.get("ok") is False:
        raise RuntimeError(f"upload failed: {result}")
    return result["data"]["filePath"]


def summarize(records: list[dict[str, Any]]) -> dict[str, Any]:
    counts = Counter()
    photos = Counter()
    unmapped: list[dict[str, Any]] = []
    ignored_images: list[dict[str, Any]] = []
    invalid_phone: list[dict[str, Any]] = []
    for record in records:
        payload = record["payload"]
        counts["records"] += 1
        counts[f"result:{payload['checkResult']}"] += 1
        if len(payload["customerPhone"]) < 4:
            invalid_phone.append({"file": record["file"].name, "row": record["row"], "name": payload["customerName"], "phone": payload["customerPhone"]})
        for item in record["pendingImages"]:
            photos[item["type"]] += 1
        for issue in record["unmappedHazards"]:
            unmapped.append({"file": record["file"].name, "row": record["row"], "issue": issue})
        if record["ignoredImageCols"]:
            ignored_images.append({"file": record["file"].name, "row": record["row"], "cols": record["ignoredImageCols"]})
    return {
        "counts": dict(counts),
        "photos": dict(photos),
        "unmappedHazards": unmapped,
        "ignoredImages": ignored_images,
        "invalidPhoneRows": invalid_phone,
    }


def execute_import(records: list[dict[str, Any]], args: argparse.Namespace) -> dict[str, Any]:
    password = args.password or getpass.getpass("后台账号密码: ")
    login = api_json(args.base_url, "/api/auth/login", payload={"phone": args.phone, "password": password})
    token = login["token"]
    imported: list[dict[str, Any]] = []
    with tempfile.TemporaryDirectory(prefix="xinyuan-old-import-") as temp_dir:
        temp_path = Path(temp_dir)
        for index, record in enumerate(records, start=1):
            payload = record["payload"]
            if args.company_id:
                payload["companyId"] = args.company_id
            for image_index, item in enumerate(record["pendingImages"], start=1):
                data, suffix = image_bytes(item["image"])
                filename = f"{payload['clientUuid']}-{image_index}{suffix}"
                local = temp_path / filename
                local.write_bytes(data)
                file_path = upload_file(args.base_url, token, filename, data)
                photo_type = item["type"]
                if photo_type == "hazard":
                    payload["photos"]["hazard"][item["hazardCode"]].append(file_path)
                else:
                    payload["photos"][photo_type].append(file_path)
            payload["photos"]["hazard"] = dict(payload["photos"]["hazard"])
            created = api_json(args.base_url, "/api/records", token=token, payload=payload)
            imported.append({"clientUuid": payload["clientUuid"], "id": created.get("id"), "file": record["file"].name, "row": record["row"]})
            print(f"[{index}/{len(records)}] imported id={created.get('id')} {payload['customerName']}")
    return {"imported": imported}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="导入旧版 APP 导出的终端用户隐患核查清单 Excel。默认只预检，不写入服务器。")
    parser.add_argument("--input-dir", default=str(DEFAULT_INPUT_DIR), help="旧 Excel 所在目录")
    parser.add_argument("--glob", default="终端用户隐患核查清单_*.xlsx", help="匹配文件名")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="系统地址，例如 https://www.gashbxy.com")
    parser.add_argument("--phone", help="后台账号手机号；--execute 时必填")
    parser.add_argument("--password", help="后台账号密码；不填则执行时提示输入")
    parser.add_argument("--company-id", type=int, default=0, help="总部管理员导入时指定公司 ID")
    parser.add_argument("--execute", action="store_true", help="真正上传图片并创建记录")
    parser.add_argument("--report", default=str(DEFAULT_REPORT), help="预检/导入报告 JSON 输出路径")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    input_dir = Path(args.input_dir)
    files = sorted(input_dir.glob(args.glob))
    if not files:
        print(f"未找到 Excel：{input_dir / args.glob}", file=sys.stderr)
        return 2
    if args.execute and not args.phone:
        print("--execute 时必须提供 --phone", file=sys.stderr)
        return 2

    records = iter_records(files)
    report = {
        "mode": "execute" if args.execute else "dry-run",
        "sourceFiles": [str(file) for file in files],
        "summary": summarize(records),
    }
    if args.execute:
        report["result"] = execute_import(records, args)

    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print(f"report: {report_path.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
