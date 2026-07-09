from __future__ import annotations

import collections
import json
from pathlib import Path

import openpyxl


FILES = [
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260707_195303(3).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260629_201259(1).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260630_192551(2).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260701_101410(3).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260701_110223(1).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260702_072407(4).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260703_202603(2).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260706_211024(1).xlsx"),
    Path(r"C:\Users\tonyh\Desktop\新建文件夹 (2)\终端用户隐患核查清单_20260706_211719(2).xlsx"),
]

CATEGORY_COLS = {
    "可燃气体报警器": 10,
    "软管": 12,
    "用气环境": 14,
    "减压阀": 16,
    "安全用气指南": 18,
}


def image_position(image: object) -> tuple[int, int] | None:
    try:
        anchor = image.anchor
        return anchor._from.row + 1, anchor._from.col + 1
    except Exception:
        return None


def main() -> None:
    grand = collections.Counter()
    all_missing: list[dict[str, object]] = []
    all_headers: set[tuple[str, ...]] = set()

    for file in FILES:
        workbook = openpyxl.load_workbook(file, data_only=True)
        sheet = workbook.active

        print(f"\n### {file.name}")
        headers: list[str] = []
        for col in range(1, sheet.max_column + 1):
            row2 = sheet.cell(2, col).value or ""
            row3 = sheet.cell(3, col).value or ""
            headers.append(f"{col}:{row2}/{row3}")
        all_headers.add(tuple(headers))
        print("headers:", " | ".join(headers))

        image_by_col: collections.Counter[int] = collections.Counter()
        images_by_row: dict[int, list[int]] = collections.defaultdict(list)
        for image in getattr(sheet, "_images", []):
            position = image_position(image)
            if not position:
                continue
            row, col = position
            image_by_col[col] += 1
            images_by_row[row].append(col)
        print("images by col:", dict(sorted(image_by_col.items())))

        stats = collections.Counter()
        missing: list[dict[str, object]] = []
        for row in range(4, sheet.max_row + 1):
            sequence = sheet.cell(row, 1).value
            name = sheet.cell(row, 2).value
            if not sequence and not name:
                continue

            stats["rows"] += 1
            result = str(sheet.cell(row, 9).value or "").strip()
            stats[f"result:{result or '空'}"] += 1
            grand["rows"] += 1
            grand[f"result:{result or '空'}"] += 1

            issues: list[tuple[str, str]] = []
            for category, col in CATEGORY_COLS.items():
                value = sheet.cell(row, col).value
                if value:
                    issues.append((category, str(value).strip()))
            gas = sheet.cell(row, 22).value
            if gas:
                issues.append(("气源排查项目", str(gas).strip()))

            if not sheet.cell(row, 20).value:
                stats["blank_delivery"] += 1
                grand["blank_delivery"] += 1

            if result == "隐患未清零" and not issues:
                item = {
                    "file": file.name,
                    "row": row,
                    "seq": sequence,
                    "name": name,
                    "phone": sheet.cell(row, 4).value,
                    "imageCols": sorted(images_by_row.get(row, [])),
                }
                missing.append(item)
                all_missing.append(item)

        print("stats:", dict(stats))
        print(
            "uncleared_without_text_count:",
            len(missing),
            "sample:",
            json.dumps(missing[:5], ensure_ascii=False, default=str),
        )

    print("\n### overall")
    print("grand:", dict(grand))
    print("header_variants:", len(all_headers))
    print("total_uncleared_without_text:", len(all_missing))
    print("uncleared_without_text_sample:", json.dumps(all_missing[:12], ensure_ascii=False, default=str))


if __name__ == "__main__":
    main()
