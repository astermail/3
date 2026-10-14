package com.gaspoweredcake.client33;

import com.gaspoweredcake.client33.utils.combat.WeaponSelection;
import com.gaspoweredcake.client33.utils.combat.WeaponSelection.*;
import com.gaspoweredcake.client33.utils.entity.DamageUtils;
import com.gaspoweredcake.client33.utils.world.RtpSearchSession;
import com.gaspoweredcake.client33.utils.world.RtpSearchSession.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import java.util.List;

public final class ClientRepairRegression {
    public static void run() {
        var sword = new Candidate(1, Kind.Sword, 6);
        var axe = new Candidate(2, Kind.Axe, 10);
        check(WeaponSelection.choose(List.of(axe), Kind.Sword, 100, 0) == 2, "Missing preferred weapon cannot leave a fist selected");
        check(WeaponSelection.choose(List.of(sword, axe), Kind.Sword, 4, 0) == 2, "Damage at the preference threshold is selectable");
        check(WeaponSelection.choose(List.of(sword, axe), Kind.Sword, 5, 0) == 1, "Preference applies below its threshold");
        check(WeaponSelection.choose(List.of(sword, axe), Kind.Any, 100, 0) == 2, "Any mode always uses highest predicted damage");
        for (Kind kind : List.of(Kind.Mace, Kind.Spear, Kind.Trident)) {
            check(WeaponSelection.choose(List.of(sword, new Candidate(5, kind, 9)), kind, 4, 0) == 5, "New melee preference is usable: " + kind);
        }
        check(WeaponSelection.choose(List.of(sword, new Candidate(3, Kind.Sword, 6)), Kind.Sword, 4, 3) == 3, "Equal candidates retain the current slot");
        check(WeaponSelection.choose(List.of(new Candidate(2, Kind.Axe, Double.NaN)), Kind.Any, 0, 4) == 4, "Invalid damage cannot pick an item");

        var original = new AttributeInstance(Attributes.ATTACK_DAMAGE, ignored -> {});
        original.setBaseValue(1);
        var heldDamage = modifier("held", 20);
        var potion = modifier("potion", 3);
        var candidateDamage = modifier("candidate", 6);
        original.addTransientModifier(heldDamage); original.addTransientModifier(potion);
        var held = ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, heldDamage, EquipmentSlotGroup.MAINHAND).build();
        var candidate = ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, candidateDamage, EquipmentSlotGroup.MAINHAND).build();
        check(DamageUtils.weaponDamageAttribute(original, held, candidate).getValue() == 10, "Candidate replaces custom held modifiers while retaining unrelated effects");
        check(original.getValue() == 24, "Damage prediction never mutates the player's attribute");

        var start = new Position(0, 64, 0, "minecraft:overworld");
        var arrival = new Position(100, 64, 0, "minecraft:overworld");
        var session = search(2);
        check(session.tick(0, start) && session.requests() == 1, "Activation requests exactly one RTP");
        check(!session.match(100), "Old origin chunks cannot stop a search before arrival");
        check(!session.tick(2000, start) && session.requests() == 1, "A queued teleport never triggers a duplicate command");
        check(!session.tick(2500, arrival) && session.state() == State.Scanning, "Observed arrival begins scanning");
        check(!session.match(30) && session.match(31), "More than30 uses a strict31-block boundary");
        check(!session.tick(50000, arrival) && session.requests() == 1, "Finding storage stops further requests permanently");
        session = search(2); session.tick(0, start);
        session.tick(1000, arrival);
        check(!session.tick(2999, arrival), "Arrival scan window must finish");
        check(!session.tick(3000, arrival) && session.state() == State.Cooling, "Scan completion still honors cooldown");
        check(!session.tick(4999, arrival) && session.tick(5000, arrival) && session.requests() == 2, "Next RTP occurs at the configured interval");
        session.tick(6000, start); session.tick(8000, start);
        check(!session.tick(10000, start) && session.state() == State.Limit, "The request limit ends an unsuccessful search");
        session = search(10); session.tick(0, start);
        check(!session.tick(10000, start) && session.state() == State.TimedOut && session.requests() == 1, "Denied or stalled RTP stops without retry spam");
        session = search(1); session.tick(0, start);
        session.tick(500, new Position(0, 64, 0, "minecraft:the_nether"));
        check(session.state() == State.Scanning, "Dimension-changing teleport can arrive at the same coordinates");
        System.out.println("Weapon selection and RTP search regressions passed.");
    }
    private static RtpSearchSession search(int max) { return new RtpSearchSession(31, max, 5000, 10000, 2000, 32); }
    private static AttributeModifier modifier(String name, double amount) { return new AttributeModifier(Identifier.fromNamespaceAndPath("test", name), amount, AttributeModifier.Operation.ADD_VALUE); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
