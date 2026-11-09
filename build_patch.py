import os
import sys
import shutil
import subprocess
import zipfile
import re
import urllib.request

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
SCRATCH = os.path.abspath(os.path.join(BASE_DIR, ".."))

# Resolve Java & Android paths
def resolve_tools():
    # Java
    javac = shutil.which("javac")
    jbr_javac = r"C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
    if not javac and os.path.exists(jbr_javac):
        javac = jbr_javac

    java = shutil.which("java")
    jbr_java = r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
    if not java and os.path.exists(jbr_java):
        java = jbr_java

    # Android SDK
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not android_home:
        candidate = os.path.expanduser(r"~\AppData\Local\Android\Sdk")
        if os.path.exists(candidate):
            android_home = candidate

    if not android_home:
        raise RuntimeError("ANDROID_HOME not found. Please set ANDROID_HOME environment variable.")

    # d8
    d8_ext = ".bat" if sys.platform == "win32" else ""
    d8_path = os.path.join(android_home, "build-tools", "34.0.0", f"d8{d8_ext}")
    if not os.path.exists(d8_path):
        # find highest build-tools version
        bt_dir = os.path.join(android_home, "build-tools")
        if os.path.exists(bt_dir):
            versions = sorted(os.listdir(bt_dir), reverse=True)
            for v in versions:
                cand = os.path.join(bt_dir, v, f"d8{d8_ext}")
                if os.path.exists(cand):
                    d8_path = cand
                    break

    android_jar = os.path.join(android_home, "platforms", "android-34", "android.jar")
    if not os.path.exists(android_jar):
        platforms_dir = os.path.join(android_home, "platforms")
        if os.path.exists(platforms_dir):
            p_versions = sorted(os.listdir(platforms_dir), reverse=True)
            for p in p_versions:
                cand = os.path.join(platforms_dir, p, "android.jar")
                if os.path.exists(cand):
                    android_jar = cand
                    break

    return javac, java, d8_path, android_jar

JAVAC, JAVA, D8, ANDROID_JAR = resolve_tools()

# Ensure base MPP and Morphe CLI exist
MORPHE_CLI = os.path.join(BASE_DIR, "morphe-desktop-2.1.1-all.jar")
if not os.path.exists(MORPHE_CLI):
    scratch_cli = os.path.join(SCRATCH, "morphe-desktop-2.1.1-all.jar")
    if os.path.exists(scratch_cli):
        MORPHE_CLI = scratch_cli
    else:
        print("[*] Downloading morphe-desktop CLI...")
        cli_url = "https://github.com/MorpheApp/morphe-desktop/releases/download/v2.1.1/morphe-desktop-2.1.1-all.jar"
        urllib.request.urlretrieve(cli_url, MORPHE_CLI)

BASE_MPP = os.path.join(BASE_DIR, "patches-2.1.0.mpp")
if not os.path.exists(BASE_MPP):
    scratch_mpp = os.path.join(SCRATCH, "patches-2.1.0.mpp")
    if os.path.exists(scratch_mpp):
        BASE_MPP = scratch_mpp
    else:
        print("[*] Downloading base Morphe patches 2.1.0...")
        mpp_url = "https://github.com/MorpheApp/morphe-patches/releases/download/v2.1.0/patches-2.1.0.mpp"
        urllib.request.urlretrieve(mpp_url, BASE_MPP)

BUILD_DIR = os.path.join(BASE_DIR, "build")
STUBS_SRC = os.path.join(BASE_DIR, "stubs")
EXT_SRC = os.path.join(BASE_DIR, "extensions", "src", "main", "java")
PATCH_SRC = os.path.join(BASE_DIR, "patches", "src", "main", "java")
RES_SRC = os.path.join(BASE_DIR, "resources")

def run(cmd, desc):
    print(f"[*] {desc}...")
    env = os.environ.copy()
    jbr = r"C:\Program Files\Android\Android Studio\jbr"
    if os.path.exists(jbr):
        env["JAVA_HOME"] = jbr
        env["PATH"] = os.path.join(jbr, "bin") + os.pathsep + env.get("PATH", "")
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True, env=env)
    if res.returncode != 0:
        print(f"[!] Error in {desc}:")
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
        raise RuntimeError(f"Step failed: {desc}")
    return res.stdout

def main():
    if os.path.exists(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    os.makedirs(BUILD_DIR)

    # 1. Unpack base MPP into build/mpp_work
    mpp_work = os.path.join(BUILD_DIR, "mpp_work")
    os.makedirs(mpp_work)
    print(f"[*] Unpacking base MPP {BASE_MPP}...")
    with zipfile.ZipFile(BASE_MPP, 'r') as zf:
        zf.extractall(mpp_work)

    # 2. Compile stubs for compilation classpath
    stub_classes = os.path.join(BUILD_DIR, "stub_classes")
    os.makedirs(stub_classes)
    stub_files = []
    for root, _, files in os.walk(STUBS_SRC):
        for f in files:
            if f.endswith(".java"):
                stub_files.append(os.path.join(root, f))
    cmd_compile_stubs = f'"{JAVAC}" --release 17 -cp "{ANDROID_JAR}" -d "{stub_classes}" ' + " ".join(f'"{f}"' for f in stub_files)
    run(cmd_compile_stubs, "Compiling stub classes for extension dependencies")

    # 3. Compile extensions against Android SDK and stubs
    ext_classes = os.path.join(BUILD_DIR, "ext_classes")
    os.makedirs(ext_classes)

    ext_java_files = []
    for root, _, files in os.walk(EXT_SRC):
        for f in files:
            if f.endswith(".java"):
                ext_java_files.append(os.path.join(root, f))

    cp_sep = ";" if sys.platform == "win32" else ":"
    cp_ext = f"{ANDROID_JAR}{cp_sep}{stub_classes}"
    cmd_compile_ext = f'"{JAVAC}" --release 17 -cp "{cp_ext}" -d "{ext_classes}" ' + " ".join(f'"{f}"' for f in ext_java_files)
    run(cmd_compile_ext, "Compiling extension Java sources")

    # 4. Convert extension classes to DEX
    ext_dex_dir = os.path.join(BUILD_DIR, "ext_dex")
    os.makedirs(ext_dex_dir)
    class_files = []
    for root, _, files in os.walk(ext_classes):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))
    cmd_d8_ext = f'"{D8}" --release --min-api 26 --output "{ext_dex_dir}" ' + " ".join(f'"{f}"' for f in class_files)
    run(cmd_d8_ext, "D8 converting extension classes to DEX")

    # 5. Merge extension DEX with existing youtube.mpe
    orig_youtube_mpe = os.path.join(mpp_work, "extensions", "youtube.mpe")
    merge_mpe_dir = os.path.join(BUILD_DIR, "merge_mpe")
    os.makedirs(merge_mpe_dir)
    tmp_orig_dex = os.path.join(merge_mpe_dir, "orig_youtube.dex")
    shutil.copyfile(orig_youtube_mpe, tmp_orig_dex)
    tmp_ext_dex = os.path.join(merge_mpe_dir, "ryu_ext.dex")
    shutil.copyfile(os.path.join(ext_dex_dir, "classes.dex"), tmp_ext_dex)

    merged_mpe_out = os.path.join(BUILD_DIR, "merged_mpe_out")
    os.makedirs(merged_mpe_out)
    cmd_merge_mpe = f'"{D8}" --release --min-api 26 --output "{merged_mpe_out}" "{tmp_orig_dex}" "{tmp_ext_dex}"'
    run(cmd_merge_mpe, "Merging extension DEX with youtube.mpe")

    # Replace youtube.mpe in mpp_work
    shutil.copyfile(os.path.join(merged_mpe_out, "classes.dex"), orig_youtube_mpe)
    print(f"[*] Updated {orig_youtube_mpe} with ReturnYouTubeUsername extension classes.")

    # 6. Compile patch definition class
    patch_classes = os.path.join(BUILD_DIR, "patch_classes")
    os.makedirs(patch_classes)
    patch_java_files = []
    for root, _, files in os.walk(PATCH_SRC):
        for f in files:
            if f.endswith(".java"):
                patch_java_files.append(os.path.join(root, f))

    cp_patch = f"{MORPHE_CLI}{cp_sep}{mpp_work}"
    cmd_compile_patch = f'"{JAVAC}" --release 17 -cp "{cp_patch}" -d "{patch_classes}" ' + " ".join(f'"{f}"' for f in patch_java_files)
    run(cmd_compile_patch, "Compiling patch definition Java sources")

    # Copy compiled .class files into mpp_work (for JVM ClassLoader)
    for root, _, files in os.walk(patch_classes):
        for f in files:
            if f.endswith(".class"):
                rel_path = os.path.relpath(os.path.join(root, f), patch_classes)
                dest = os.path.join(mpp_work, rel_path)
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                shutil.copyfile(os.path.join(root, f), dest)
    print("[*] Copied patch definition .class files into mpp_work for desktop classloader.")

    # 7. Convert patch definition to DEX and merge with mpp_work/classes.dex
    patch_dex_dir = os.path.join(BUILD_DIR, "patch_dex")
    os.makedirs(patch_dex_dir)
    patch_class_files = []
    for root, _, files in os.walk(patch_classes):
        for f in files:
            if f.endswith(".class"):
                patch_class_files.append(os.path.join(root, f))
    cmd_d8_patch = f'"{D8}" --release --min-api 26 --output "{patch_dex_dir}" ' + " ".join(f'"{f}"' for f in patch_class_files)
    run(cmd_d8_patch, "D8 converting patch definition classes to DEX")

    orig_mpp_dex = os.path.join(mpp_work, "classes.dex")
    merge_mpp_dir = os.path.join(BUILD_DIR, "merge_mpp")
    os.makedirs(merge_mpp_dir)
    tmp_orig_mpp_dex = os.path.join(merge_mpp_dir, "orig_mpp.dex")
    shutil.copyfile(orig_mpp_dex, tmp_orig_mpp_dex)
    tmp_patch_dex = os.path.join(merge_mpp_dir, "patch.dex")
    shutil.copyfile(os.path.join(patch_dex_dir, "classes.dex"), tmp_patch_dex)

    merged_mpp_out = os.path.join(BUILD_DIR, "merged_mpp_out")
    os.makedirs(merged_mpp_out)
    cmd_merge_mpp = f'"{D8}" --release --min-api 26 --output "{merged_mpp_out}" "{tmp_orig_mpp_dex}" "{tmp_patch_dex}"'
    run(cmd_merge_mpp, "Merging patch definition DEX with classes.dex")

    shutil.copyfile(os.path.join(merged_mpp_out, "classes.dex"), orig_mpp_dex)
    print(f"[*] Updated {orig_mpp_dex} with ReturnYouTubeUsername patch DEX.")

    # 8. Merge resources
    def append_xml_content(target_file, snippet_file):
        with open(snippet_file, "r", encoding="utf-8") as f:
            snippet_content = f.read()

        inner_match = re.search(r"<resources>(.*?)</resources>", snippet_content, re.DOTALL)
        if not inner_match:
            return
        inner_content = inner_match.group(1).strip()

        if os.path.exists(target_file):
            with open(target_file, "r", encoding="utf-8") as f:
                target_content = f.read()
            pos = target_content.rfind("</resources>")
            if pos != -1:
                new_content = target_content[:pos] + "\n    " + inner_content + "\n" + target_content[pos:]
            else:
                new_content = target_content + "\n<resources>\n    " + inner_content + "\n</resources>"
        else:
            os.makedirs(os.path.dirname(target_file), exist_ok=True)
            new_content = f"<resources>\n    {inner_content}\n</resources>"

        with open(target_file, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"[*] Injected resources into {target_file}")

    # Inject default English
    append_xml_content(
        os.path.join(mpp_work, "addresources", "values", "youtube", "strings.xml"),
        os.path.join(RES_SRC, "values", "strings.xml")
    )
    append_xml_content(
        os.path.join(mpp_work, "addresources", "values", "youtube", "arrays.xml"),
        os.path.join(RES_SRC, "values", "arrays.xml")
    )

    # Inject Traditional Chinese
    append_xml_content(
        os.path.join(mpp_work, "addresources", "values-zh-rTW", "youtube", "strings.xml"),
        os.path.join(RES_SRC, "values-zh-rTW", "strings.xml")
    )
    append_xml_content(
        os.path.join(mpp_work, "addresources", "values-zh-rTW", "youtube", "arrays.xml"),
        os.path.join(RES_SRC, "values-zh-rTW", "arrays.xml")
    )

    # Inject Simplified Chinese
    append_xml_content(
        os.path.join(mpp_work, "addresources", "values-zh-rCN", "youtube", "strings.xml"),
        os.path.join(RES_SRC, "values-zh-rCN", "strings.xml")
    )
    append_xml_content(
        os.path.join(mpp_work, "addresources", "values-zh-rCN", "youtube", "arrays.xml"),
        os.path.join(RES_SRC, "values-zh-rCN", "arrays.xml")
    )

    # 9. Repackage into final MPP
    out_mpp = os.path.join(BASE_DIR, "patches-2.1.0-with-return-youtube-username.mpp")
    if os.path.exists(out_mpp):
        os.remove(out_mpp)

    print(f"[*] Packaging final MPP bundle to {out_mpp}...")
    with zipfile.ZipFile(out_mpp, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(mpp_work):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, mpp_work)
                zf.write(full_path, rel_path)

    # 10. Sign MPP bundle with Ed25519 signature
    private_key = os.environ.get("MORPHE_PRIVATE_KEY", "ZAC2EP/Tqdmb1Fm7Sxj/s5XVXNLeZFPLaP5HfJJLYvI=")
    signer_java = os.path.join(BASE_DIR, "SignBundle.java")
    cmd_sign = f'"{JAVA}" -cp "{MORPHE_CLI}" "{signer_java}" "{out_mpp}" "{private_key}"'
    sign_res = run(cmd_sign, "Signing MPP bundle with Ed25519")
    print(sign_res.strip())

    print(f"[+] Successfully built and signed: {out_mpp} ({os.path.getsize(out_mpp):,} bytes)")

if __name__ == "__main__":
    main()
