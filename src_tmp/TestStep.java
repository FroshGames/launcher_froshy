import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.AbstractStep;
import net.raphimc.minecraftauth.step.msa.StepLocalWebServer;
import net.raphimc.minecraftauth.util.MicrosoftConstants;
import net.lenni0451.commons.httpclient.HttpClient;

public class TestStep {
    public static void main(String[] args) {
        AbstractStep<StepLocalWebServer.LocalWebServerCallback, StepFullJavaSession.FullJavaSession> authStep = MinecraftAuth.builder()
            .withClientId("04b07795-8ddb-461a-bbee-02f9e1bf7b46")
            .withScope("XboxLive.signin offline_access")
            .localWebServer()
            .withoutDeviceToken()
            .regularAuthentication(MicrosoftConstants.JAVA_XSTS_RELYING_PARTY)
            .buildMinecraftJavaProfileStep(true);
            
        System.out.println(authStep.name);
    }
}

