package ru.invest.api.bond.supplier.model;

/**
 * Какие облигации: все, рублёвые или валютные - как в адресе списка /bonds/{all|local|foreign}.
 */
public enum BondScope {
    ALL,
    LOCAL,
    FOREIGN
}
