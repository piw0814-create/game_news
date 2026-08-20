package com.gamenews.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_aliases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false, unique = true, length = 255)
    private String alias;

    private GameAlias(Game game, String alias) {
        this.game = game;
        this.alias = alias;
    }

    public static GameAlias of(Game game, String alias) {
        return new GameAlias(game, alias);
    }

    public void reassignGame(Game targetGame) {
        this.game = targetGame;
    }
}
