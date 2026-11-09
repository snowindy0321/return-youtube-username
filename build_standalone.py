import os
import shutil
import zipfile

SCRATCH = r"C:\Users\Snowindy\.gemini\antigravity\scratch"
WORKSPACE = os.path.join(SCRATCH, "return-youtube-username")
BUILD_DIR = os.path.join(WORKSPACE, "build")
RES_SRC = os.path.join(WORKSPACE, "resources")

def main():
    standalone_dir = os.path.join(BUILD_DIR, "standalone_mpp")
    if os.path.exists(standalone_dir):
        shutil.rmtree(standalone_dir)
    os.makedirs(standalone_dir)

    # 1. Copy patch definition .class
    patch_classes = os.path.join(BUILD_DIR, "patch_classes")
    for root, _, files in os.walk(patch_classes):
        for f in files:
            if f.endswith(".class"):
                rel_path = os.path.relpath(os.path.join(root, f), patch_classes)
                dest = os.path.join(standalone_dir, rel_path)
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                shutil.copyfile(os.path.join(root, f), dest)

    # 2. Copy patch definition DEX as classes.dex
    patch_dex = os.path.join(BUILD_DIR, "patch_dex", "classes.dex")
    shutil.copyfile(patch_dex, os.path.join(standalone_dir, "classes.dex"))

    # 3. Copy extension DEX as extensions/youtube.mpe
    ext_dex = os.path.join(BUILD_DIR, "ext_dex", "classes.dex")
    ext_out_dir = os.path.join(standalone_dir, "extensions")
    os.makedirs(ext_out_dir, exist_ok=True)
    shutil.copyfile(ext_dex, os.path.join(ext_out_dir, "youtube.mpe"))

    # 4. Copy addresources
    def copy_res(lang, name):
        src = os.path.join(RES_SRC, lang, name)
        if os.path.exists(src):
            dst_dir = os.path.join(standalone_dir, "addresources", lang, "youtube")
            os.makedirs(dst_dir, exist_ok=True)
            shutil.copyfile(src, os.path.join(dst_dir, name))

    copy_res("values", "strings.xml")
    copy_res("values", "arrays.xml")
    copy_res("values-zh-rTW", "strings.xml")
    copy_res("values-zh-rTW", "arrays.xml")
    copy_res("values-zh-rCN", "strings.xml")
    copy_res("values-zh-rCN", "arrays.xml")

    # 5. Zip into return-youtube-username-1.0.0.mpp
    out_mpp = os.path.join(SCRATCH, "return-youtube-username-1.0.0.mpp")
    if os.path.exists(out_mpp):
        os.remove(out_mpp)

    with zipfile.ZipFile(out_mpp, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, _, files in os.walk(standalone_dir):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, standalone_dir)
                zf.write(full_path, rel_path)

    print(f"[+] Successfully built standalone: {out_mpp} ({os.path.getsize(out_mpp):,} bytes)")

if __name__ == "__main__":
    main()
