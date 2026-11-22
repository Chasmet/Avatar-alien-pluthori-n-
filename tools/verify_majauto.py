#!/usr/bin/env python3
"""Contrôles MAJAUTO avant compilation : interface, intégrité et garde-fous APK."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/fr/chk/alienlive"
manager=(src/"UpdateManager.java").read_text(encoding="utf-8")
ui=(src/"UpdateProgress.java").read_text(encoding="utf-8")
layout=(root/"app/src/main/res/layout/dialog_update_progress.xml").read_text(encoding="utf-8")
workflow=(root/".github/workflows/android.yml").read_text(encoding="utf-8")
for text in ('DOWNLOADING.compareAndSet(false,true)', 'verifyApkArchive(partial)',
             'getPackageArchiveInfo(apk.getAbsolutePath(),0)', 'expectedSha',
             'progress.bytes(received,total)', 'download(activity,url,sha256,remote,'):
    assert text in manager, "MAJAUTO : garde-fou absent "+text
for text in ('cancelled', 'int percent=', '%.1f / %.1f Mo', 'void fail(', 'void close('):
    assert text in ui, "MAJAUTO : suivi absent "+text
for text in ('@+id/updateProgress', '@+id/updateState', '@+id/updateDetails'):
    assert text in layout, "MAJAUTO : vue absente "+text
for text in ('apksigner', 'unzip -tqq', 'sha256sum', 'aapt'):
    assert text in workflow, "MAJAUTO : contrôle CI absent "+text
print("OK MAJAUTO : pourcentage/MB/annulation, SHA, ZIP, APK et signature conservés")
