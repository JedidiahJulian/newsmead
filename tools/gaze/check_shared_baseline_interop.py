"""Check synthetic packages emitted by SharedBaselineInteropTest with the real writer.

No phone, network, tuning, or private recordings. Run the named JVM test first.
"""
import argparse
import json
import math
from pathlib import Path

import study_notebook as notebook


def check(latest):
    root = Path(json.loads(Path(latest).read_text(encoding="utf-8"))["root"])
    result = notebook.coordinate_analysis(root, "evaluation-session")
    assert result["fit"]["status"] == "READY", result["reference_issue"]
    values = [row["result"]["md2"] for row in result["points"]]
    assert len(values) == 3
    assert all(row["result"]["status"] == "AVAILABLE" for row in result["points"])
    assert all(math.isclose(value, expected, rel_tol=1e-10, abs_tol=1e-10)
               for value, expected in zip(values, (0.0, 2.0, 4.0))), values
    summary = result["summaries"][0]["result"]
    assert summary["status"] == "AVAILABLE", summary
    assert math.isclose(summary["mean_md2"], 2.0, rel_tol=1e-10)
    assert result["rdf_availability"] == {"UNAVAILABLE": 1}
    return dict(check="kotlin_writer_python_shared_reference_v1", synthetic_only=True,
                importer_version=notebook.sp.VERSION, fit_status=result["fit"]["status"],
                point_md2=values, mean_md2=summary["mean_md2"],
                rdf_status_counts=result["rdf_availability"],
                source_manifest_sha256=result["reference_source_manifest_sha256"],
                source_root=str(root), core_config=result["config"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--latest", type=Path, default=Path("app/build/shared-baseline-interop/latest.json"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    receipt = check(args.latest)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(receipt, indent=2))
