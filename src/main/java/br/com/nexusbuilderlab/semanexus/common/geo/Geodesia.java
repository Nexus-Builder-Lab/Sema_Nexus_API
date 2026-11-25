package br.com.nexusbuilderlab.semanexus.common.geo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Cálculos geoespaciais simples (WGS84, esfera de raio médio) para as ferramentas de agentes: distância de
 * Haversine, caixa envolvente (pré-filtro em SQL) e posição de um ponto em relação a um polígono GeoJSON.
 * Para raios de dezenas de km a aproximação equiretangular local usada nos polígonos tem erro desprezível.
 */
public final class Geodesia {

    public static final double RAIO_TERRA_KM = 6371.0088;
    private static final double KM_POR_GRAU = Math.PI * RAIO_TERRA_KM / 180.0;

    private Geodesia() {
    }

    /** Caixa envolvente em graus: {latMin, latMax, lonMin, lonMax}. */
    public record Caixa(double latMin, double latMax, double lonMin, double lonMax) {
    }

    /** Distância de círculo máximo em km. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Caixa que contém o círculo de {@code raioKm} (mais {@code margemKm}) em torno do ponto. */
    public static Caixa caixa(double lat, double lon, double raioKm, double margemKm) {
        double r = raioKm + margemKm;
        double dLat = r / KM_POR_GRAU;
        double dLon = r / (KM_POR_GRAU * Math.max(0.01, Math.cos(Math.toRadians(lat))));
        return new Caixa(lat - dLat, lat + dLat, lon - dLon, lon + dLon);
    }

    /** Anéis (exteriores e furos) de um Polygon/MultiPolygon GeoJSON como listas de {lon, lat}; vazia se inválido. */
    @SuppressWarnings("unchecked")
    public static List<List<double[]>> aneis(Map<String, Object> geometria) {
        return aneis(geometria, false);
    }

    /** Linhas de um LineString/MultiLineString GeoJSON como listas de {lon, lat}; vazia se inválido. */
    @SuppressWarnings("unchecked")
    public static List<List<double[]>> linhas(Map<String, Object> geometria) {
        List<List<double[]>> out = new ArrayList<>();
        if (geometria == null || !(geometria.get("coordinates") instanceof List<?> coords)) {
            return out;
        }
        try {
            Object tipo = geometria.get("type");
            if ("LineString".equals(tipo)) {
                out.add(pontosDe((List<Object>) coords));
            } else if ("MultiLineString".equals(tipo)) {
                for (Object l : coords) {
                    out.add(pontosDe((List<Object>) l));
                }
            }
        } catch (ClassCastException | NullPointerException e) {
            return new ArrayList<>();
        }
        out.removeIf(l -> l.size() < 2);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<double[]> pontosDe(List<Object> lista) {
        List<double[]> pontos = new ArrayList<>();
        for (Object p : lista) {
            List<Object> c = (List<Object>) p;
            pontos.add(new double[] {((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue()});
        }
        return pontos;
    }

    /** Como {@link #aneis(Map)}; com {@code soExteriores}, só o primeiro anel de cada polígono (sem furos). */
    @SuppressWarnings("unchecked")
    public static List<List<double[]>> aneis(Map<String, Object> geometria, boolean soExteriores) {
        List<List<double[]>> aneis = new ArrayList<>();
        if (geometria == null || !(geometria.get("coordinates") instanceof List<?> coords)) {
            return aneis;
        }
        try {
            if ("Polygon".equals(geometria.get("type"))) {
                adicionar(aneis, (List<Object>) coords, soExteriores);
            } else if ("MultiPolygon".equals(geometria.get("type"))) {
                for (Object poligono : coords) {
                    adicionar(aneis, (List<Object>) poligono, soExteriores);
                }
            }
        } catch (ClassCastException | NullPointerException e) {
            return new ArrayList<>();
        }
        return aneis;
    }

    @SuppressWarnings("unchecked")
    private static void adicionar(List<List<double[]>> destino, List<Object> aneisDoPoligono, boolean soExterior) {
        for (Object anel : soExterior ? aneisDoPoligono.subList(0, Math.min(1, aneisDoPoligono.size())) : aneisDoPoligono) {
            List<double[]> pontos = new ArrayList<>();
            for (Object p : (List<Object>) anel) {
                List<Object> c = (List<Object>) p;
                pontos.add(new double[] {((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue()});
            }
            destino.add(pontos);
        }
    }

   /** Centroide aproximado (média dos vértices dos anéis exteriores de todos os polígonos, sem repetir o vértice de fechamento). */
    public static double[] centroide(Map<String, Object> geometria) {
        double lat = 0;
        double lon = 0;
        int n = 0;
        for (List<double[]> anel : aneis(geometria, true)) {
            int fim = anel.size() > 1 && Arrays.equals(anel.get(0), anel.get(anel.size() - 1)) ? anel.size() - 1 : anel.size();
            for (int k = 0; k < fim; k++) {
                lon += anel.get(k)[0];
                lat += anel.get(k)[1];
                n++;
            }
        }
        return n == 0 ? null : new double[] {lat / n, lon / n};
    }

    /** Caixa envolvente de todos os vértices da geometria, ampliada em {@code raioKm}; {@code null} se inválida. */
    public static Caixa caixaDaGeometria(Map<String, Object> geometria, double raioKm) {
        double latMin = 90, latMax = -90, lonMin = 180, lonMax = -180;
        boolean algum = false;
        for (List<double[]> anel : aneis(geometria, true)) {
            for (double[] p : anel) {
                latMin = Math.min(latMin, p[1]);
                latMax = Math.max(latMax, p[1]);
                lonMin = Math.min(lonMin, p[0]);
                lonMax = Math.max(lonMax, p[0]);
                algum = true;
            }
        }
        if (!algum) {
            return null;
        }
        double dLat = raioKm / KM_POR_GRAU;
        double dLon = raioKm / (KM_POR_GRAU * Math.max(0.01, Math.cos(Math.toRadians(Math.max(Math.abs(latMin), Math.abs(latMax))))));
        return new Caixa(latMin - dLat, latMax + dLat, lonMin - dLon, lonMax + dLon);
    }

    /**
     * Distância aproximada entre duas áreas: 0 se algum vértice de uma está dentro da outra ou se arestas se cruzam;
     * senão a menor distância de um vértice (amostrado, no máximo ~200 por área) de uma até a outra. {@code NaN} se
     * alguma geometria é inválida.
     */
    public static double distanciaEntreAreasKm(Map<String, Object> a, Map<String, Object> b) {
        double d1 = distanciaDosVertices(a, b);
        double d2 = distanciaDosVertices(b, a);
        if (Double.isNaN(d1) || Double.isNaN(d2)) {
            return Double.NaN;
        }
        double min = Math.min(d1, d2);
        if (min > 0 && arestasSeCruzam(aneis(a, true), aneis(b, true))) {
            return 0;
        }
        return min;
    }

    /** Distância em km do ponto a qualquer segmento de LineString/MultiLineString; {@code NaN} se inválida. */
    public static double distanciaALinhaKm(double lat, double lon, Map<String, Object> geometria) {
        List<List<double[]>> linhas = linhas(geometria);
        if (linhas.isEmpty()) {
            return Double.NaN;
        }
        return menorDistanciaSegmentos(lat, lon, linhas);
    }

    /**
     * Distância aproximada em km entre uma área (Polygon/MultiPolygon) e uma linha (LineString/MultiLineString).
     * Retorna 0 se algum vértice da linha cai dentro da área ou se algum segmento da linha cruza uma aresta da área.
     */
    public static double distanciaAreaALinhaKm(Map<String, Object> area, Map<String, Object> linha) {
        List<List<double[]>> aneisArea = aneis(area);
        List<List<double[]>> linhas = linhas(linha);
        if (aneisArea.isEmpty() || linhas.isEmpty()) {
            return Double.NaN;
        }
        double min = Double.MAX_VALUE;
        List<double[]> ptsLinha = new ArrayList<>();
        linhas.forEach(ptsLinha::addAll);
        int passoL = Math.max(1, ptsLinha.size() / 200);
        for (int i = 0; i < ptsLinha.size(); i += passoL) {
            double[] p = ptsLinha.get(i);
            double d = distanciaAoPoligonoKm(p[1], p[0], area);
            if (d == 0) {
                return 0;
            }
            min = Math.min(min, d);
        }
        List<double[]> ptsArea = new ArrayList<>();
        aneis(area, true).forEach(ptsArea::addAll);
        int passoA = Math.max(1, ptsArea.size() / 200);
        for (int i = 0; i < ptsArea.size(); i += passoA) {
            double[] p = ptsArea.get(i);
            min = Math.min(min, menorDistanciaSegmentos(p[1], p[0], linhas));
        }
        if (min > 0 && arestasSeCruzam(aneis(area, true), linhas)) {
            return 0;
        }
        return min;
    }

    /** Distância do ponto a qualquer geometria suportada (Polygon, MultiPolygon, LineString, MultiLineString). */
    public static double distanciaPontoAGeometriaKm(double lat, double lon, Map<String, Object> geometria) {
        if (geometria == null) {
            return Double.NaN;
        }
        Object tipo = geometria.get("type");
        if ("LineString".equals(tipo) || "MultiLineString".equals(tipo)) {
            return distanciaALinhaKm(lat, lon, geometria);
        }
        return distanciaAoPoligonoKm(lat, lon, geometria);
    }

    /** Distância entre uma área e outra geometria (polígono ou linha). */
    public static double distanciaEntreGeometriasKm(Map<String, Object> area, Map<String, Object> outra) {
        if (area == null || outra == null) {
            return Double.NaN;
        }
        Object tipo = outra.get("type");
        if ("LineString".equals(tipo) || "MultiLineString".equals(tipo)) {
            return distanciaAreaALinhaKm(area, outra);
        }
        return distanciaEntreAreasKm(area, outra);
    }

    private static double menorDistanciaSegmentos(double lat, double lon, List<List<double[]>> caminhos) {
        double kmLon = KM_POR_GRAU * Math.cos(Math.toRadians(lat));
        double min = Double.MAX_VALUE;
        for (List<double[]> caminho : caminhos) {
            for (int i = 0; i + 1 < caminho.size(); i++) {
                double[] a = caminho.get(i);
                double[] b = caminho.get(i + 1);
                min = Math.min(min, distanciaSegmentoKm((a[0] - lon) * kmLon, (a[1] - lat) * KM_POR_GRAU,
                        (b[0] - lon) * kmLon, (b[1] - lat) * KM_POR_GRAU));
            }
        }
        return min;
    }

    private static boolean arestasSeCruzam(List<List<double[]>> caminhosA, List<List<double[]>> caminhosB) {
        if (caminhosA.isEmpty() || caminhosB.isEmpty()) {
            return false;
        }
        Caixa caB = caixaBruta(caminhosA);
        Caixa cbB = caixaBruta(caminhosB);
        if (caB == null || cbB == null || caB.lonMax() < cbB.lonMin() || cbB.lonMax() < caB.lonMin()
                || caB.latMax() < cbB.latMin() || cbB.latMax() < caB.latMin()) {
            return false;
        }
        for (List<double[]> ca : caminhosA) {
            for (int i = 0; i + 1 < ca.size(); i++) {
                double[] p1 = ca.get(i);
                double[] p2 = ca.get(i + 1);
                double s1LonMin = Math.min(p1[0], p2[0]), s1LonMax = Math.max(p1[0], p2[0]);
                double s1LatMin = Math.min(p1[1], p2[1]), s1LatMax = Math.max(p1[1], p2[1]);
                if (s1LonMax < cbB.lonMin() || cbB.lonMax() < s1LonMin || s1LatMax < cbB.latMin()
                        || cbB.latMax() < s1LatMin) {
                    continue;
                }
                for (List<double[]> cb : caminhosB) {
                    for (int j = 0; j + 1 < cb.size(); j++) {
                        double[] q1 = cb.get(j);
                        double[] q2 = cb.get(j + 1);
                        if (Math.max(q1[0], q2[0]) < s1LonMin || s1LonMax < Math.min(q1[0], q2[0])
                                || Math.max(q1[1], q2[1]) < s1LatMin || s1LatMax < Math.min(q1[1], q2[1])) {
                            continue;
                        }
                        if (segmentosSeCruzam(p1[0], p1[1], p2[0], p2[1], q1[0], q1[1], q2[0], q2[1])) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Caixa envolvente exata (sem margem) de caminhos {lon, lat}; {@code null} se vazia. */
    public static Caixa caixaBruta(List<List<double[]>> caminhos) {
        double latMin = 90, latMax = -90, lonMin = 180, lonMax = -180;
        boolean algum = false;
        for (List<double[]> c : caminhos) {
            for (double[] p : c) {
                latMin = Math.min(latMin, p[1]);
                latMax = Math.max(latMax, p[1]);
                lonMin = Math.min(lonMin, p[0]);
                lonMax = Math.max(lonMax, p[0]);
                algum = true;
            }
        }
        return algum ? new Caixa(latMin, latMax, lonMin, lonMax) : null;
    }

    /** Caixa envolvente de qualquer geometria suportada (Polygon, MultiPolygon, LineString, MultiLineString). */
    public static Caixa caixaDeQualquerGeometria(Map<String, Object> geometria, double raioKm) {
        List<List<double[]>> caminhos = aneis(geometria, true);
        if (caminhos.isEmpty()) {
            caminhos = linhas(geometria);
        }
        Caixa bruta = caixaBruta(caminhos);
        if (bruta == null) {
            return null;
        }
        if (raioKm <= 0) {
            return bruta;
        }
        double dLat = raioKm / KM_POR_GRAU;
        double dLon = raioKm / (KM_POR_GRAU * Math.max(0.01,
                Math.cos(Math.toRadians(Math.max(Math.abs(bruta.latMin()), Math.abs(bruta.latMax()))))));
        return new Caixa(bruta.latMin() - dLat, bruta.latMax() + dLat, bruta.lonMin() - dLon, bruta.lonMax() + dLon);
    }

    /** Centroide aproximado de polígono ou ponto médio de linha; {@code null} se inválido. */
    public static double[] centroideDeQualquerGeometria(Map<String, Object> geometria) {
        double[] c = centroide(geometria);
        if (c != null) {
            return c;
        }
        double lat = 0;
        double lon = 0;
        int n = 0;
        for (List<double[]> linha : linhas(geometria)) {
            for (double[] p : linha) {
                lon += p[0];
                lat += p[1];
                n++;
            }
        }
        return n == 0 ? null : new double[] {lat / n, lon / n};
    }

    private static boolean segmentosSeCruzam(double ax, double ay, double bx, double by,
                                             double cx, double cy, double dx, double dy) {
        double o1 = orientacao(ax, ay, bx, by, cx, cy);
        double o2 = orientacao(ax, ay, bx, by, dx, dy);
        double o3 = orientacao(cx, cy, dx, dy, ax, ay);
        double o4 = orientacao(cx, cy, dx, dy, bx, by);
        if ((o1 > 0 && o2 < 0 || o1 < 0 && o2 > 0) && (o3 > 0 && o4 < 0 || o3 < 0 && o4 > 0)) {
            return true;
        }
        return (o1 == 0 && noSegmento(ax, ay, bx, by, cx, cy))
                || (o2 == 0 && noSegmento(ax, ay, bx, by, dx, dy))
                || (o3 == 0 && noSegmento(cx, cy, dx, dy, ax, ay))
                || (o4 == 0 && noSegmento(cx, cy, dx, dy, bx, by));
    }

    private static double orientacao(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static boolean noSegmento(double ax, double ay, double bx, double by, double px, double py) {
        return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
                && py >= Math.min(ay, by) && py <= Math.max(ay, by);
    }

    private static double distanciaDosVertices(Map<String, Object> de, Map<String, Object> para) {
        List<double[]> vertices = new ArrayList<>();
        aneis(de, true).forEach(vertices::addAll);
        if (vertices.isEmpty() || aneis(para).isEmpty()) {
            return Double.NaN;
        }
        int passo = Math.max(1, vertices.size() / 200);
        double min = Double.MAX_VALUE;
        for (int i = 0; i < vertices.size(); i += passo) {
            min = Math.min(min, distanciaAoPoligonoKm(vertices.get(i)[1], vertices.get(i)[0], para));
            if (min == 0) {
                break;
            }
        }
        return min;
    }

    /**
     * Distância em km do ponto à geometria: 0 se o ponto está dentro (respeitando furos), senão a menor distância
     * até uma aresta. {@code NaN} se a geometria é inválida.
     */
    public static double distanciaAoPoligonoKm(double lat, double lon, Map<String, Object> geometria) {
        List<List<double[]>> aneis = aneis(geometria);
        if (aneis.isEmpty()) {
            return Double.NaN;
        }
        if (dentro(lat, lon, aneis)) {
            return 0;
        }
        return menorDistanciaSegmentos(lat, lon, aneis);
    }

    /** Ponto dentro de algum anel externo e fora dos furos (regra par-ímpar sobre todos os anéis). */
    private static boolean dentro(double lat, double lon, List<List<double[]>> aneis) {
        boolean dentro = false;
        for (List<double[]> anel : aneis) {
            for (int i = 0, j = anel.size() - 1; i < anel.size(); j = i++) {
                double xi = anel.get(i)[0];
                double yi = anel.get(i)[1];
                double xj = anel.get(j)[0];
                double yj = anel.get(j)[1];
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                    dentro = !dentro;
                }
            }
        }
        return dentro;
    }

    /** Distância da origem (0,0) ao segmento AB, em km (coordenadas já projetadas em km). */
    private static double distanciaSegmentoKm(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }
}
