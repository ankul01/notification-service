package notification.domain;

import java.util.EnumSet;
import java.util.Set;

/** Which channels a recipient allows, split by message priority. */
public final class RecipientPreferences {
    private final Set<ChannelType> optedOutChannels;
    private final boolean promotionalOptIn;

    public RecipientPreferences(Set<ChannelType> optedOutChannels, boolean promotionalOptIn) {
        this.optedOutChannels = optedOutChannels == null ? EnumSet.noneOf(ChannelType.class)
                : EnumSet.copyOf(optedOutChannels);
        this.promotionalOptIn = promotionalOptIn;
    }

    public boolean allows(ChannelType channel, Priority priority) {
        if (optedOutChannels.contains(channel)) {
            return false;
        }
        return priority == Priority.TRANSACTIONAL || promotionalOptIn;
    }
}
