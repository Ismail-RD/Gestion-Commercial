import { useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { droits } from '../auth/droits';
import {
  Button,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import StarIcon from '@mui/icons-material/Star';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import DownloadIcon from '@mui/icons-material/Download';
import DeleteIcon from '@mui/icons-material/Delete';
import {
  supprimerFicheTechnique,
  telechargerFicheTechnique,
  trouverProduit,
  uploaderFicheTechnique,
} from '../api/produits';
import { apercuStock } from '../api/stock';
import { formatMontant } from '../utils/format';
import { Champ, Champs, DetailLayout, Section } from '../components/DetailView';

export default function ProduitDetailPage() {
  const { id } = useParams();
  const produitId = Number(id);
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { user } = useAuth();
  const mesDroits = droits(user?.role);

  const { data: produit, isLoading, isError } = useQuery({
    queryKey: ['produit', produitId],
    queryFn: () => trouverProduit(produitId),
    enabled: !Number.isNaN(produitId),
  });

  const uploadMutation = useMutation({
    mutationFn: (fichier: File) => uploaderFicheTechnique(produitId, fichier),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['produit', produitId] }),
  });

  const supprimerFicheMutation = useMutation({
    mutationFn: () => supprimerFicheTechnique(produitId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['produit', produitId] }),
  });

  const telecharger = async () => {
    const blob = await telechargerFicheTechnique(produitId);
    // Le telechargement passe par l'API (JWT requis) : on ouvre le blob obtenu.
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  };

  // Vue stock du produit : la référence est unique -> l'apercu renvoie ses
  // quantités dans les 5 dépôts (0 inclus) + le total.
  const { data: apercu } = useQuery({
    queryKey: ['produit-apercu-stock', produit?.reference],
    queryFn: () => apercuStock({ reference: produit!.reference, size: 1 }),
    enabled: !!produit?.reference,
  });

  const apercuProduit = apercu?.content?.[0];
  const lignesStock = apercuProduit?.depots ?? [];
  const stockTotal = apercuProduit?.stockTotal ?? 0;
  const reservee = apercuProduit?.quantiteReservee ?? 0;
  const disponible = apercuProduit?.disponible ?? stockTotal;

  return (
    <DetailLayout
      titre={produit?.reference ?? 'Produit'}
      sousTitre={produit?.designation}
      retour="/produits"
      isLoading={isLoading}
      isError={isError}
    >
      {produit && (
        <>
          <Section titre="Informations">
            <Champs>
              <Champ label="Référence" valeur={produit.reference} />
              <Champ label="Désignation" valeur={produit.designation} />
              <Champ label="Prix unitaire HT" valeur={formatMontant(produit.prixUnitaireHT)} />
              <Champ label="Taux de TVA" valeur={`${produit.tauxTVA} %`} />
              <Champ label="Unité de mesure" valeur={produit.uniteMesure} />
              <Champ label="Catégorie" valeur={produit.categorieNom} />
              <Champ label="Description" valeur={produit.description} />
            </Champs>
          </Section>

          <Section titre="Fiche technique">
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf,image/jpeg,image/png"
              style={{ display: 'none' }}
              onChange={(e) => {
                const fichier = e.target.files?.[0];
                if (fichier) uploadMutation.mutate(fichier);
                e.target.value = '';
              }}
            />
            {produit.ficheTechnique ? (
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip label="Fiche disponible" color="success" size="small" />
                <Button size="small" startIcon={<DownloadIcon />} onClick={telecharger}>
                  Telecharger
                </Button>
                {/* La fiche technique fait partie du catalogue produit */}
                {mesDroits.ecrireCatalogue && (
                  <>
                    <Button
                      size="small"
                      startIcon={<UploadFileIcon />}
                      onClick={() => fileInputRef.current?.click()}
                      disabled={uploadMutation.isPending}
                    >
                      Remplacer
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      startIcon={<DeleteIcon />}
                      onClick={() => supprimerFicheMutation.mutate()}
                      disabled={supprimerFicheMutation.isPending}
                    >
                      Supprimer
                    </Button>
                  </>
                )}
              </Stack>
            ) : (
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <Typography color="text.secondary">Aucune fiche technique</Typography>
                {mesDroits.ecrireCatalogue && (
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<UploadFileIcon />}
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploadMutation.isPending}
                  >
                    Ajouter (PDF, JPG, PNG)
                  </Button>
                )}
              </Stack>
            )}
            {uploadMutation.isError && (
              <Typography color="error" variant="body2" sx={{ mt: 1 }}>
                Envoi impossible (format non autorise ou fichier trop volumineux).
              </Typography>
            )}
          </Section>

          <Section titre="Marques">
            {produit.marques.length === 0 ? (
              <Typography color="text.secondary">Aucune marque</Typography>
            ) : (
              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                {produit.marques.map((m) => (
                  <Chip key={m.id} label={m.nom} />
                ))}
              </Stack>
            )}
          </Section>

          <Section titre="Fournisseurs">
            {produit.fournisseurs.length === 0 ? (
              <Typography color="text.secondary">Aucun fournisseur</Typography>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Fournisseur</TableCell>
                    <TableCell>Référence chez le fournisseur</TableCell>
                    <TableCell align="center">Principal</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {produit.fournisseurs.map((f) => (
                    <TableRow key={f.fournisseurId}>
                      <TableCell>{f.fournisseurNom}</TableCell>
                      <TableCell>{f.referenceFournisseur ?? '-'}</TableCell>
                      <TableCell align="center">
                        {f.estPrincipal && <StarIcon fontSize="small" color="warning" />}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Section>

          <Section titre={`Stock par dépôt (total : ${stockTotal} · réservé : ${reservee} · disponible : ${disponible})`}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Dépôt</TableCell>
                  <TableCell align="right">Quantité</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {lignesStock.map((s) => (
                  <TableRow key={s.depotCode}>
                    <TableCell>Depot {s.depotCode}</TableCell>
                    <TableCell align="right">
                      <Chip
                        label={s.quantite}
                        size="small"
                        color={s.quantite === 0 ? 'error' : s.quantite < 10 ? 'warning' : 'success'}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Section>
        </>
      )}
    </DetailLayout>
  );
}
