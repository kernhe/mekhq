/*
 * Copyright (c) 2020-2021 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.campaign.market.unitMarket;

import megamek.common.MechSummary;
import megamek.common.annotations.Nullable;
import megamek.common.util.EncodeControl;
import mekhq.MekHQ;
import mekhq.MekHqXmlUtil;
import mekhq.campaign.Campaign;
import mekhq.campaign.market.enums.UnitMarketMethod;
import mekhq.campaign.market.enums.UnitMarketType;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.UnitGeneratorParameters;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public abstract class AbstractUnitMarket implements Serializable {
    //region Variable Declarations
    private static final long serialVersionUID = 1583989355384117937L;

    private final UnitMarketMethod method;
    private List<UnitMarketOffer> offers;

    protected final ResourceBundle resources = ResourceBundle.getBundle("mekhq.resources.Market", new EncodeControl());
    //endregion Variable Declarations

    //region Constructors
    protected AbstractUnitMarket(final UnitMarketMethod method) {
        this.method = method;
        setOffers(new ArrayList<>());
    }
    //endregion Constructors

    //region Getters/Setters
    public UnitMarketMethod getMethod() {
        return method;
    }

    public List<UnitMarketOffer> getOffers() {
        return offers;
    }

    public void setOffers(final List<UnitMarketOffer> offers) {
        this.offers = offers;
    }
    //endregion Getters/Setters

    //region Process New Day
    /**
     * This is the primary method for processing the Unit Market. It is executed as part of
     * {@link Campaign#newDay()}
     * @param campaign the campaign to process the Unit Market new day using
     */
    public abstract void processNewDay(final Campaign campaign);

    //region Generate Offers
    /**
     * This is the primary Unit Market generation method, which is how the market specified
     * generates unit offers
     * @param campaign the campaign to generate the unit offers for
     */
    public abstract void generateUnitOffers(final Campaign campaign);

    /**
     * This adds a number of unit offers
     * @param campaign the campaign to add the offers based on
     * @param number the number of units to generate
     * @param market the unit market type the unit is part of
     * @param unitType the unit type to generate
     * @param faction the faction to add the offers for, or null. If null, that must be handled within
     *                this method before any generated offers may be added to the market.
     * @param quality the quality of the unit to generate
     * @param priceTarget the target number used to determine the percent
     */
    protected abstract void addOffers(final Campaign campaign, final int number,
                                      UnitMarketType market, final int unitType,
                                      @Nullable Faction faction, final int quality,
                                      final int priceTarget);

    /**
     * @param campaign the campaign to use to generate the unit
     * @param market the market type the unit is being offered in
     * @param unitType the unit type to generate the unit with
     * @param faction the faction to generate the unit from
     * @param quality the quality to generate the unit at
     * @param percent the percentage of the original unit cost the unit will be offered at
     * @return the name of the unit that has been added to the market, or null if none were added
     */
    public @Nullable String addSingleUnit(final Campaign campaign, final UnitMarketType market,
                                          final int unitType, final Faction faction,
                                          final int quality, final int percent) {
        final UnitGeneratorParameters parameters = createUnitGeneratorParameters(campaign, unitType, faction, quality);
        parameters.setWeightClass(generateWeight(campaign, unitType, faction));
        return addSingleUnit(campaign, market, unitType, parameters, percent);
    }

    /**
     * @param campaign the campaign to use to generate the unit
     * @param market the market type the unit is being offered in
     * @param unitType the unit type to generate the unit with
     * @param parameters the parameters with which to generate the unit
     * @param percent the percentage of the original unit cost the unit will be offered at
     * @return the name of the unit that has been added to the market, or null if none were added
     */
    public @Nullable String addSingleUnit(final Campaign campaign, final UnitMarketType market,
                                          final int unitType, final UnitGeneratorParameters parameters,
                                          final int percent) {
        final MechSummary mechSummary = campaign.getUnitGenerator().generate(parameters);
        return (mechSummary == null) ? null : addSingleUnit(market, unitType, mechSummary, percent);
    }

    /**
     * @param market the market type the unit is being offered in
     * @param unitType the unit type of the generated unit
     * @param mechSummary the generated mech summary
     * @param percent the percentage of the original unit cost the unit will be offered at
     * @return the name of the unit that has been added to the market
     */
    public String addSingleUnit(final UnitMarketType market, final int unitType,
                                final MechSummary mechSummary, final int percent) {
        getOffers().add(new UnitMarketOffer(market, unitType, mechSummary, percent));
        return mechSummary.getName();
    }

    /**
     * @param campaign the campaign to create the parameters based on
     * @param unitType the unit type to generate
     * @param faction the faction to generate for
     * @param quality the quality of the unit to generate
     * @return the created parameters to use in generating the unit
     */
    protected UnitGeneratorParameters createUnitGeneratorParameters(final Campaign campaign,
                                                                    final int unitType,
                                                                    final Faction faction,
                                                                    final int quality) {
        UnitGeneratorParameters parameters = new UnitGeneratorParameters();
        parameters.setFaction(faction.getShortName());
        parameters.setYear(campaign.getGameYear());
        parameters.setUnitType(unitType);
        parameters.setQuality(quality);
        parameters.setFilter(ms ->
                (!campaign.getCampaignOptions().limitByYear() || (campaign.getGameYear() > ms.getYear()))
                        && (!ms.isClan() || campaign.getCampaignOptions().allowClanPurchases())
                        && (ms.isClan() || campaign.getCampaignOptions().allowISPurchases()));
        return parameters;
    }

    /**
     * @param campaign the campaign to generate the unit weight based on
     * @param unitType the unit type to determine the format of weight to generate
     * @param faction the faction to generate the weight for
     * @return the generated weight
     */
    public abstract int generateWeight(final Campaign campaign, final int unitType,
                                       final Faction faction);

    /**
     * @param campaign the campaign to write the refresh report to
     */
    protected void writeRefreshReport(final Campaign campaign) {
        if (campaign.getCampaignOptions().getUnitMarketReportRefresh()) {
            campaign.addReport(resources.getString("AbstractUnitMarket.RefreshReport.report"));
        }
    }
    //endregion Generate Offers

    //region Offer Removal
    /**
     * This is the primary Unit Market removal method, which is how the market specified
     * removes unit offers
     * @param campaign the campaign to use in determining the offers to remove
     */
    public abstract void removeUnitOffers(final Campaign campaign);
    //endregion Offer Removal
    //endregion Process New Day

    //region File I/O
    /**
     * This writes the Unit Market to XML
     * @param pw the PrintWriter to write to
     * @param indent the base indent level to write at
     */
    public void writeToXML(final PrintWriter pw, int indent) {
        MekHqXmlUtil.writeSimpleXMLOpenIndentedLine(pw, indent++, "unitMarket");
        writeBodyToXML(pw, indent);
        MekHqXmlUtil.writeSimpleXMLCloseIndentedLine(pw, --indent, "unitMarket");
    }


    /**
     * This is meant to be overridden so that a market can have additional elements added to it,
     * albeit with this called by super.writeBodyToXML(pw, indent) first.
     * @param pw the PrintWriter to write to
     * @param indent the base indent level to write at
     */
    protected void writeBodyToXML(final PrintWriter pw, int indent) {
        for (final UnitMarketOffer offer : getOffers()) {
            offer.writeToXML(pw, indent);
        }
    }

    /**
     * This method fills the market based on the supplied XML node. The market is initialized as
     * empty before this is called.
     * @param wn the node to fill the market from
     */
    public void fillFromXML(final Node wn) {
        try {
            final NodeList nl = wn.getChildNodes();
            for (int x = 0; x < nl.getLength(); x++) {
                final Node wn2 = nl.item(x);
                if (wn2.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                parseXMLNode(wn2);
            }
        } catch (Exception e) {
            MekHQ.getLogger().error("Failed to parse Unit Market, keeping currently parsed market", e);
        }
    }

    /**
     * This is meant to be overridden so that a market can have additional elements added to it,
     * albeit with this called by super.parseXMLNode(wn) first.
     * @param wn the node to parse from XML
     */
    protected void parseXMLNode(final Node wn) {
        if (wn.getNodeName().equalsIgnoreCase("offer")) {
            getOffers().add(UnitMarketOffer.generateInstanceFromXML(wn));
        }
    }
    //endregion File I/O
}
