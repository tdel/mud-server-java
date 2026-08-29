package app.domain.world;

public final class NormalZone extends AbstractZone {

    public static final NormalZone INSTANCE = new NormalZone();

    private NormalZone() {
    }

    @Override
    public String getName() {
        return "Normal";
    }
}
