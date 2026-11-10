import os
import sys
import shutil
import subprocess
import zipfile
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

# Ensure Morphe CLI exists
MORPHE_CLI = os.path.join(BASE_DIR, "morphe-desktop-2.1.1-all.jar")
if not os.path.exists(MORPHE_CLI):
    scratch_cli = os.path.join(SCRATCH, "morphe-desktop-2.1.1-all.jar")
    if os.path.exists(scratch_cli):
        MORPHE_CLI = scratch_cli
    else:
        print("[*] Downloading morphe-desktop CLI...")
        cli_url = "https://github.com/MorpheApp/morphe-desktop/releases/download/v2.1.1/morphe-desktop-2.1.1-all.jar"
        urllib.request.urlretrieve(cli_url, MORPHE_CLI)

BUILD_DIR = os.path.join(BASE_DIR, "build")
if os.path.exists(BUILD_DIR):
    shutil.rmtree(BUILD_DIR)
os.makedirs(BUILD_DIR)

def run(cmd, desc):
    print(f"[*] {desc}...")
    env = os.environ.copy()
    if sys.platform == "win32" and "Android Studio" in JAVA:
        jbr = r"C:\Program Files\Android\Android Studio\jbr"
        env["JAVA_HOME"] = jbr
        env["PATH"] = os.path.join(jbr, "bin") + os.pathsep + env.get("PATH", "")
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True, env=env)
    if res.returncode != 0:
        print("STDOUT:", res.stdout)
        print("STDERR:", res.stderr)
        raise RuntimeError(f"Step failed: {desc}")
    return res.stdout

def main():
    print("=== Building Pure Standalone Morphe Patch: Return YouTube Username ===")

    # 1. Compile extension sources
    ext_src = os.path.join(BASE_DIR, "extensions", "src", "main", "java")
    stubs_src = os.path.join(BASE_DIR, "stubs")
    ext_classes = os.path.join(BUILD_DIR, "ext_classes")
    os.makedirs(ext_classes)

    stubs_files = []
    for root, _, files in os.walk(stubs_src):
        for f in files:
            if f.endswith(".java"):
                stubs_files.append(os.path.join(root, f))

    ext_files = []
    for root, _, files in os.walk(ext_src):
        for f in files:
            if f.endswith(".java"):
                ext_files.append(os.path.join(root, f))

    ext_filelist = os.path.join(BUILD_DIR, "ext_filelist.txt")
    with open(ext_filelist, "w", encoding="utf-8") as f:
        f.write("\n".join(stubs_files + ext_files))

    cmd_javac_ext = f'"{JAVAC}" --release 8 -cp "{ANDROID_JAR}" -d "{ext_classes}" @"{ext_filelist}"'
    run(cmd_javac_ext, "Compiling extension sources")

    # 2. D8: Convert extension classes to DEX
    ext_dex_dir = os.path.join(BUILD_DIR, "ext_dex")
    os.makedirs(ext_dex_dir)

    ext_class_files = []
    for root, _, files in os.walk(ext_classes):
        for f in files:
            if f.endswith(".class") and "app/morphe/extension/youtube" in root.replace("\\", "/"):
                ext_class_files.append(os.path.join(root, f))

    cmd_d8_ext = f'"{D8}" --release --min-api 26 --output "{ext_dex_dir}" ' + " ".join(f'"{f}"' for f in ext_class_files)
    run(cmd_d8_ext, "D8 converting extension classes to DEX")

    # 3. Compile patch definition sources
    patch_src = os.path.join(BASE_DIR, "patches", "src", "main", "java")
    patch_classes = os.path.join(BUILD_DIR, "patch_classes")
    os.makedirs(patch_classes)

    patch_files = []
    for root, _, files in os.walk(patch_src):
        for f in files:
            if f.endswith(".java"):
                patch_files.append(os.path.join(root, f))

    patch_filelist = os.path.join(BUILD_DIR, "patch_filelist.txt")
    with open(patch_filelist, "w", encoding="utf-8") as f:
        f.write("\n".join(patch_files))

    cmd_javac_patch = f'"{JAVAC}" --release 17 -cp "{MORPHE_CLI}" -d "{patch_classes}" @"{patch_filelist}"'
    run(cmd_javac_patch, "Compiling patch definition")

    # 4. Convert patch classes to DEX
    patch_dex_dir = os.path.join(BUILD_DIR, "patch_dex")
    os.makedirs(patch_dex_dir)

    patch_class_files = []
    for root, _, files in os.walk(patch_classes):
        for f in files:
            if f.endswith(".class"):
                patch_class_files.append(os.path.join(root, f))

    cmd_d8_patch = f'"{D8}" --release --min-api 26 --output "{patch_dex_dir}" ' + " ".join(f'"{f}"' for f in patch_class_files)
    run(cmd_d8_patch, "D8 converting patch classes to DEX")

    # 5. Assemble standalone MPP package
    mpp_work = os.path.join(BUILD_DIR, "mpp_work")
    os.makedirs(mpp_work)

    # Copy patch .class files (for desktop JVM classloader)
    for root, _, files in os.walk(patch_classes):
        for f in files:
            if f.endswith(".class"):
                rel = os.path.relpath(os.path.join(root, f), patch_classes)
                dest = os.path.join(mpp_work, rel)
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                shutil.copyfile(os.path.join(root, f), dest)

    # Copy patch classes.dex (for Dalvik / Android)
    shutil.copyfile(os.path.join(patch_dex_dir, "classes.dex"), os.path.join(mpp_work, "classes.dex"))

    # Create extensions/youtube.mpe
    ext_dir = os.path.join(mpp_work, "extensions")
    os.makedirs(ext_dir)
    shutil.copyfile(os.path.join(ext_dex_dir, "classes.dex"), os.path.join(ext_dir, "youtube.mpe"))

    # Copy resources
    res_src = os.path.join(BASE_DIR, "resources")
    for lang in ["values", "values-zh-rTW", "values-zh-rCN"]:
        for rfile in ["strings.xml", "arrays.xml"]:
            sf = os.path.join(res_src, lang, rfile)
            if os.path.exists(sf):
                df = os.path.join(mpp_work, "addresources", lang, "youtube", rfile)
                os.makedirs(os.path.dirname(df), exist_ok=True)
                shutil.copyfile(sf, df)

    # Write MANIFEST.MF
    manifest_dir = os.path.join(mpp_work, "META-INF")
    os.makedirs(manifest_dir)
    with open(os.path.join(manifest_dir, "MANIFEST.MF"), "w", encoding="utf-8") as f:
        f.write("Manifest-Version: 1.0\nName: Return YouTube Username\nVersion: 2.2.6\n")

    # 7. Package standalone MPP bundle
    out_mpp = os.path.join(BASE_DIR, "return-youtube-username.mpp")
    if os.path.exists(out_mpp):
        os.remove(out_mpp)

    with zipfile.ZipFile(out_mpp, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(mpp_work):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, mpp_work)
                zf.write(full_path, rel_path)

    # 8. Sign bundle
    signer_java = os.path.join(BASE_DIR, "SignBundle.java")
    private_key = os.environ.get("MORPHE_PRIVATE_KEY", "ZAC2EP/Tqdmb1Fm7Sxj/s5XVXNLeZFPLaP5HfJJLYvI=")
    cmd_sign = f'"{JAVA}" -cp "{MORPHE_CLI}" "{signer_java}" "{out_mpp}" "{private_key}"'
    run(cmd_sign, "Signing standalone MPP")

    print(f"[+] Successfully built pure standalone MPP: {out_mpp} ({os.path.getsize(out_mpp):,} bytes)")

if __name__ == "__main__":
    main()
