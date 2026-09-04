package dev.sellerkit.reconkit.core.pass;

import dev.sellerkit.reconkit.core.MatchState;
import dev.sellerkit.reconkit.domain.enums.MatchPass;

public interface MatchingPass {

    MatchPass id();

    void apply(MatchState state);
}
