/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.resource;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.DeletedConsumerDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.resource.server.v1.DeletedConsumerApi;
import org.candlepin.resource.util.ResourceDateParser;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.xnap.commons.i18n.I18n;

import java.util.Objects;

/**
 * DeletedConsumerResource
 */
public class DeletedConsumerResource implements DeletedConsumerApi {
    private final DeletedConsumerCurator deletedConsumerCurator;
    private final ModelTranslator translator;
    private final Provider<I18n> i18nProvider;

    @Inject
    public DeletedConsumerResource(DeletedConsumerCurator deletedConsumerCurator,
        ModelTranslator translator, Provider<I18n> i18nProvider) {
        this.deletedConsumerCurator = Objects.requireNonNull(deletedConsumerCurator);
        this.translator = Objects.requireNonNull(translator);
        this.i18nProvider = Objects.requireNonNull(i18nProvider);
    }

    @Override
    public CandlepinQuery<DeletedConsumerDTO> listByDate(String dateStr) {
        return this.translator.translateQuery(dateStr != null ?
            this.deletedConsumerCurator.findByDate(
                ResourceDateParser.parseDateString(this.i18nProvider.get(), dateStr)) :
            this.deletedConsumerCurator.listAll(), DeletedConsumerDTO.class);
    }
}
