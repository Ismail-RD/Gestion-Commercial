import { useQuery } from '@tanstack/react-query';
import { libelle, TYPE_TIERS } from '../utils/libelles';
import { useParams } from 'react-router-dom';
import { Chip } from '@mui/material';
import { trouverFournisseur } from '../api/fournisseurs';
import { Champ, Champs, DetailLayout, Section } from '../components/DetailView';

export default function FournisseurDetailPage() {
  const { id } = useParams();
  const fournisseurId = Number(id);

  const { data: fournisseur, isLoading, isError } = useQuery({
    queryKey: ['fournisseur', fournisseurId],
    queryFn: () => trouverFournisseur(fournisseurId),
    enabled: !Number.isNaN(fournisseurId),
  });

  const estEntreprise = fournisseur?.typeFournisseur === 'ENTREPRISE';

  return (
    <DetailLayout
      titre={fournisseur?.nom ?? 'Fournisseur'}
      sousTitre={
        fournisseur && (
          <Chip
            label={libelle(TYPE_TIERS, fournisseur.typeFournisseur)}
            size="small"
            color={estEntreprise ? 'primary' : 'default'}
          />
        )
      }
      retour="/fournisseurs"
      isLoading={isLoading}
      isError={isError}
    >
      {fournisseur && (
        <>
          <Section titre="Coordonnees">
            <Champs>
              <Champ label="Nom" valeur={fournisseur.nom} />
              <Champ label="Email" valeur={fournisseur.email} />
              <Champ
                label="Téléphones"
                valeur={fournisseur.telephones?.length ? fournisseur.telephones.join(', ') : undefined}
              />
              <Champ label="Adresse" valeur={fournisseur.adresse} />
              <Champ
                label="Enregistré le"
                valeur={
                  fournisseur.dateCreation
                    ? new Date(fournisseur.dateCreation).toLocaleDateString('fr-FR')
                    : undefined
                }
              />
              <Champ
                label="RIB"
                valeur={
                  fournisseur.ribs?.length
                    ? fournisseur.ribs
                        .map((r) => (r.banque ? `${r.rib} (${r.banque})` : r.rib))
                        .join(' · ')
                    : undefined
                }
              />
            </Champs>
          </Section>

          {estEntreprise ? (
            <Section titre="Entreprise">
              <Champs>
                <Champ label="Raison sociale" valeur={fournisseur.raisonSociale} />
                <Champ label="ICE" valeur={fournisseur.ice} />
                <Champ label="Identifiant fiscal" valeur={fournisseur.identifiantFiscal} />
              </Champs>
            </Section>
          ) : (
            <Section titre="Particulier">
              <Champs>
                <Champ label="Prénom" valeur={fournisseur.prenom} />
                <Champ label="CIN" valeur={fournisseur.cin} />
              </Champs>
            </Section>
          )}
        </>
      )}
    </DetailLayout>
  );
}
