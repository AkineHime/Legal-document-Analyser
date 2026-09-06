"""
Generates samples/sample_service_agreement.pdf: a synthetic Indian service agreement used as the
Milestone 1 ingestion fixture. Entirely fictional - no real parties, no copied text.

    pip install fpdf2
    python samples/generate_sample.py
"""
from pathlib import Path
from fpdf import FPDF
from fpdf.enums import XPos, YPos


def _cell(pdf, height, text, align="L"):
    pdf.multi_cell(0, height, text, align=align, new_x=XPos.LMARGIN, new_y=YPos.NEXT)

TITLE = "MASTER SERVICE AGREEMENT"

CLAUSES = [
    ("1. PARTIES",
     "This Master Service Agreement (\"Agreement\") is made on 1 April 2025 at Bengaluru, "
     "Karnataka, between Acme Technologies Private Limited, a company incorporated under the "
     "Companies Act, 2013 and having its registered office at 12 MG Road, Bengaluru 560001 "
     "(\"Service Provider\"), and Beta Ventures LLP, a limited liability partnership having its "
     "principal place of business at 45 Anna Salai, Chennai 600002 (\"Client\")."),
    ("2. SCOPE OF SERVICES",
     "The Service Provider shall provide software development and maintenance services as "
     "described in each Statement of Work executed by the parties. Each Statement of Work forms "
     "part of this Agreement."),
    ("3. TERM AND RENEWAL",
     "This Agreement commences on the Effective Date and continues for an initial term of "
     "twelve (12) months. Thereafter it shall renew automatically for successive twelve (12) "
     "month periods unless either party gives written notice of non-renewal at least ninety "
     "(90) days before the end of the then-current term."),
    ("4. FEES AND PAYMENT",
     "The Client shall pay the Service Provider fees of INR 5,00,000 (Rupees Five Lakh) per "
     "month, payable within fifteen (15) days of receipt of invoice. Overdue amounts carry "
     "interest at 2% per month. All fees are exclusive of applicable GST."),
    ("5. INDEMNITY",
     "The Client shall indemnify, defend and hold harmless the Service Provider and its "
     "officers, employees and agents from and against any and all claims, losses, liabilities "
     "and expenses arising out of the Client's use of the deliverables, without any monetary "
     "cap and regardless of the cause of such claims. The Service Provider's aggregate "
     "liability under this Agreement shall not exceed one month's fees."),
    ("6. CONFIDENTIALITY",
     "Each party shall keep confidential all non-public information disclosed by the other "
     "party and shall not use it except for the purpose of performing this Agreement. This "
     "obligation survives termination for a period of three (3) years."),
    ("7. TERMINATION",
     "The Service Provider may terminate this Agreement for convenience at any time on thirty "
     "(30) days written notice. The Client may terminate only for material breach that remains "
     "uncured for sixty (60) days after written notice. On termination the Client shall pay all "
     "fees accrued up to the date of termination."),
    ("8. GOVERNING LAW AND DISPUTE RESOLUTION",
     "This Agreement shall be governed by and construed in accordance with the laws of India. "
     "Any dispute arising out of or in connection with this Agreement shall be referred to and "
     "finally resolved by arbitration under the Arbitration and Conciliation Act, 1996. The "
     "seat and venue of arbitration shall be Bengaluru and the language shall be English. The "
     "courts at Bengaluru shall have exclusive jurisdiction."),
    ("9. FORCE MAJEURE",
     "Neither party shall be liable for any failure or delay in performance caused by events "
     "beyond its reasonable control, including acts of God, war, epidemic, or action of "
     "government."),
    ("10. ENTIRE AGREEMENT",
     "This Agreement, together with all Statements of Work, constitutes the entire agreement "
     "between the parties and supersedes all prior discussions. Any amendment must be in "
     "writing and signed by both parties."),
]

SIGN_BLOCK = (
    "IN WITNESS WHEREOF the parties have executed this Agreement on the date first written "
    "above.\n\nFor Acme Technologies Private Limited\nName: R. Iyer\nTitle: Director\n\n"
    "For Beta Ventures LLP\nName: S. Khan\nTitle: Designated Partner"
)


def build(out_path: Path) -> None:
    pdf = FPDF(format="A4", unit="mm")
    pdf.set_auto_page_break(auto=True, margin=20)
    pdf.set_margins(20, 20, 20)
    pdf.add_page()

    pdf.set_font("Helvetica", "B", 15)
    _cell(pdf, 9, TITLE, align="C")
    pdf.ln(4)

    for heading, body in CLAUSES:
        pdf.set_font("Helvetica", "B", 11)
        _cell(pdf, 6, heading)
        pdf.set_font("Helvetica", "", 11)
        _cell(pdf, 6, body)
        pdf.ln(3)

    pdf.ln(4)
    pdf.set_font("Helvetica", "", 11)
    _cell(pdf, 6, SIGN_BLOCK)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    pdf.output(str(out_path))
    print(f"wrote {out_path} ({out_path.stat().st_size} bytes, {pdf.page} page(s))")


RENTAL = [
    ("1. PARTIES",
     "This Leave and Licence Agreement is made on 5 May 2025 at Pune, Maharashtra between "
     "Deshmukh Estates Private Limited (the Licensor) and Meera Krishnan (the Licensee)."),
    ("2. LICENCE FEE",
     "The Licensee shall pay a monthly licence fee of INR 45,000 by the 5th of each month. "
     "A default in payment for two consecutive months attracts interest at 3% per month."),
    ("3. SECURITY DEPOSIT",
     "The Licensee shall deposit INR 2,70,000 as an interest-free security deposit, refundable "
     "within 30 days of vacating after deduction for damage and dues."),
    ("4. TERM",
     "The licence is for 11 months and may be renewed by mutual consent. It shall not be "
     "construed to create any tenancy or interest in the premises."),
    ("5. TERMINATION",
     "The Licensor may terminate on one month notice. The Licensee may terminate only after "
     "completing 6 months and on two months notice, failing which the deposit is forfeited."),
    ("6. GOVERNING LAW",
     "This agreement is governed by the laws of India and the courts at Pune shall have "
     "exclusive jurisdiction. Any dispute shall be referred to arbitration by a sole arbitrator "
     "under the Arbitration and Conciliation Act, 1996 seated at Pune."),
    ("7. MAINTENANCE",
     "The Licensee shall keep the premises in good condition and shall not make structural "
     "changes. Ordinary wear and tear is excepted."),
]

NDA = [
    ("1. PARTIES",
     "This Mutual Non-Disclosure Agreement is entered into between Nimbus Analytics LLP and "
     "Orion Robotics Private Limited, each a party."),
    ("2. CONFIDENTIAL INFORMATION",
     "Confidential Information means any non-public technical or business information disclosed "
     "by one party to the other, whether orally or in writing, and marked or reasonably "
     "understood to be confidential."),
    ("3. OBLIGATIONS",
     "The receiving party shall use the Confidential Information only to evaluate a potential "
     "business relationship and shall not disclose it to third parties without prior written "
     "consent. These obligations survive for five years after disclosure."),
    ("4. DATA PROTECTION",
     "Where Confidential Information includes personal data, each party shall comply with the "
     "Digital Personal Data Protection Act, 2023 and process such data only as necessary for "
     "the stated purpose."),
    ("5. INDEMNITY",
     "Each party shall indemnify the other for direct losses caused by its breach of this "
     "agreement, subject to a cap equal to INR 10,00,000."),
    ("6. NO LICENCE",
     "Nothing in this agreement grants either party any right, title or interest in the other's "
     "intellectual property."),
    ("7. GOVERNING LAW",
     "This agreement is governed by the laws of India and the courts at Hyderabad shall have "
     "exclusive jurisdiction."),
]


def _build_from(clauses, title, out_path):
    global TITLE, CLAUSES, SIGN_BLOCK
    TITLE, CLAUSES = title, clauses
    SIGN_BLOCK = "Signed by the duly authorised representatives of the parties."
    build(out_path)


if __name__ == "__main__":
    here = Path(__file__).resolve().parent
    build(here / "sample_service_agreement.pdf")
    _build_from(RENTAL, "LEAVE AND LICENCE AGREEMENT", here / "sample_rental_agreement.pdf")
    _build_from(NDA, "MUTUAL NON-DISCLOSURE AGREEMENT", here / "sample_nda.pdf")
