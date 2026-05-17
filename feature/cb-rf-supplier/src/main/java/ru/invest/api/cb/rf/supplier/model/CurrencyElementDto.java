package ru.invest.api.cb.rf.supplier.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.invest.api.cb.rf.supplier.adapter.BigDecimalAdapter;

import java.math.BigDecimal;

@XmlRootElement(name = "Valute")
@XmlAccessorType(XmlAccessType.FIELD)
@Data
@NoArgsConstructor
public class CurrencyElementDto {
    @XmlAttribute(name = "ID")
    private String id;

    @XmlElement(name = "NumCode")
    private String numCode;

    @XmlElement(name = "CharCode")
    private String charCode;

    @XmlElement(name = "Nominal")
    @XmlJavaTypeAdapter(BigDecimalAdapter.class)
    private BigDecimal nominal;

    @XmlElement(name = "Name")
    private String name;

    @XmlElement(name = "Value")
    @XmlJavaTypeAdapter(BigDecimalAdapter.class)
    private BigDecimal value;

    @XmlElement(name = "VunitRate")
    private String vunitRate;
}
