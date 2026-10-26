# Bank logo assets

These SVGs identify the five bank providers in YamanTracker's disabled, Coming Soon provider cards. They do not indicate a partnership, a working bank connection or endorsement. The banks retain their trademarks. No bank approval is claimed.

## Provenance and license

Downloaded unchanged on 2026-10-26 from the PayMongo PHLogos repository, revision `d8178b8cf09c688ad17e1d3ab27ef7efd854fd8e`:

| Local asset | Source path |
| --- | --- |
| bpi.svg | logos/bpi/bpi.svg |
| bdo.svg | logos/bdo/bdo.svg |
| metrobank.svg | logos/metrobank/metrobank.svg |
| security-bank.svg | logos/security_bank/security_bank.svg |
| unionbank.svg | logos/unionbank/unionbank.svg |

Source: https://github.com/paymongo-archive/phlogos/tree/d8178b8cf09c688ad17e1d3ab27ef7efd854fd8e/logos

PHLogos distributes these files under its MIT license. The complete notice is retained in `PHLOGOS-LICENSE.txt`. This is a third-party logo library, not a bank-issued permission or an assertion that the banks waive trademark rights. Public official-site logo files were investigated; no explicit general reuse grant was established for them, so this implementation uses the licensed library assets instead. The artwork was not recreated, cropped, recolored or converted.

All five files were parsed and checked for scripts, event handlers, external references, foreignObject, embedded images and text/font dependencies; none were found. They are rendered as local img sources, not inserted as untrusted HTML. CSS uses object-fit: contain and a white panel in both themes to preserve proportions, colors and contrast. No bank requests occur when displaying a logo.

## Generic icon and demo details

Other Bank uses Angular Material mat-icon with the `account_balance` SVG registered from Google's static Material icon geometry. Its source is https://github.com/google/material-design-icons/blob/master/src/action/account_balance/materialicons/24px.svg and its Apache 2.0 license is retained in `MATERIAL-ICONS-LICENSE.txt`. The geometry is consolidated into a local static path; no remote font is needed.

Connection details resolve their logo from the same provider metadata as the cards, using the stable server-provided providerId. The current API identifies its simulation as Metrobank (`metrobank`), so its details use the same Metrobank logo with the SIMULATION indicator. Unknown providers use the generic account_balance icon. Logo display does not create a connection or change its status.
