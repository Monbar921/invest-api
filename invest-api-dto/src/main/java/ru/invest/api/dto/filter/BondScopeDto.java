package ru.invest.api.dto.filter;

/**
 * Какие облигации: все, рублёвые или валютные - как в адресе списка /bonds/{all|local|foreign}.
 */
public enum BondScopeDto {
    ALL,
    LOCAL,
    FOREIGN
}
