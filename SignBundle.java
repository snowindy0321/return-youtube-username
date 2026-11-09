import app.morphe.patcher.patch.PatchBundleSignature;
import java.io.File;

public class SignBundle {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: SignBundle <mpp_path> <base64_private_key>");
            System.exit(1);
        }
        File mppFile = new File(args[0]);
        String privateKey = args[1];
        String verified = PatchBundleSignature.INSTANCE.verify(mppFile);
        if (verified != null) {
            System.out.println("Bundle is already signed. Current public key: " + verified);
            return;
        }
        String pubKey = PatchBundleSignature.INSTANCE.sign(mppFile, privateKey);
        System.out.println("Signed successfully. Public key: " + pubKey);
        verified = PatchBundleSignature.INSTANCE.verify(mppFile);
        if (!pubKey.equals(verified)) {
            System.err.println("Verification failed! " + pubKey + " != " + verified);
            System.exit(2);
        }
        System.out.println("Verified successfully: " + verified);
    }
}
